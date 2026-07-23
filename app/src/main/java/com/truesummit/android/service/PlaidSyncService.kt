package com.truesummit.android.service

import android.content.Context
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.*
import com.truesummit.android.data.model.AccountType
import com.truesummit.android.data.model.LiabilityKind
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class PlaidSyncService(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    private val plaidStorage = PlaidStorage(context)
    private val plaidApi = PlaidService.api

    data class FullSyncResult(
        val accounts: Int = 0,
        val transactionsAdded: Int = 0,
        val transactionsModified: Int = 0,
        val transactionsRemoved: Int = 0,
        val holdings: Int = 0,
        val investmentTransactions: Int = 0,
        val liabilities: Int = 0
    )

    suspend fun syncAll(
        item: StoredPlaidItem,
        includeInvestments: Boolean = true,
        includeLiabilities: Boolean = true
    ): FullSyncResult {
        val accounts = syncAccounts(item)
        val txResult = syncTransactions(item)
        var holdingsCount = 0
        var investTxCount = 0
        var liabilitiesCount = 0

        if (includeInvestments) {
            holdingsCount = syncHoldings(item)
            investTxCount = syncInvestmentTransactions(item)
        }
        if (includeLiabilities) {
            liabilitiesCount = syncLiabilities(item)
        }

        return FullSyncResult(
            accounts = accounts.size,
            transactionsAdded = txResult.added,
            transactionsModified = txResult.modified,
            transactionsRemoved = txResult.removed,
            holdings = holdingsCount,
            investmentTransactions = investTxCount,
            liabilities = liabilitiesCount
        )
    }

    // MARK: Peek / merge (pre-link helpers)

    data class PendingPlaidAccount(
        val plaidAccount: PlaidAccount,
        val alreadyLinked: Boolean,
        val mappedType: AccountType,
    ) {
        val id: String get() = plaidAccount.account_id
        val displayName: String get() = plaidAccount.official_name ?: plaidAccount.name
        val balance: BigDecimal get() = BigDecimal(
            (plaidAccount.balances.current ?: plaidAccount.balances.available ?: 0.0).toString()
        )
        val currencyCode: String get() = plaidAccount.balances.iso_currency_code ?: "USD"
    }

    suspend fun peekAccounts(item: StoredPlaidItem): List<PendingPlaidAccount> {
        val response = plaidApi.getAccounts(item.accessToken)
        return response.accounts.map { plaidAccount ->
            val link = db.plaidLinkDao().getAccountLink(plaidAccount.account_id)
            PendingPlaidAccount(
                plaidAccount = plaidAccount,
                alreadyLinked = link != null,
                mappedType = mapAccountType(plaidAccount.type, plaidAccount.subtype)
            )
        }
    }

    suspend fun unlinkedManualAccounts(): List<AccountEntity> {
        val allAccounts = db.accountDao().getAll().first()
        val linkedIds = db.plaidLinkDao().getAllAccountLinks().map { it.accountModelId }.toSet()
        return allAccounts.filter { it.id !in linkedIds }.sortedBy { it.name }
    }

    suspend fun mergePlaidAccount(
        plaidAccountId: String,
        plaidItemId: String,
        account: AccountEntity,
        currentBalance: BigDecimal
    ) {
        val existing = db.plaidLinkDao().getAccountLink(plaidAccountId)
        if (existing != null) {
            db.plaidLinkDao().insertAccountLink(
                existing.copy(
                    accountModelId = account.id,
                    plaidItemId = plaidItemId,
                    lastBalance = currentBalance,
                    updatedAt = Date()
                )
            )
        } else {
            db.plaidLinkDao().insertAccountLink(
                PlaidAccountLinkEntity(
                    plaidAccountId = plaidAccountId,
                    plaidItemId = plaidItemId,
                    accountModelId = account.id,
                    lastBalance = currentBalance
                )
            )
        }
    }

    // MARK: Accounts

    private suspend fun syncAccounts(item: StoredPlaidItem): List<AccountEntity> {
        val response = plaidApi.getAccounts(item.accessToken)
        val results = mutableListOf<AccountEntity>()
        for (plaidAccount in response.accounts) {
            results.add(upsertAccount(plaidAccount, item.itemId))
        }
        return results
    }

    private suspend fun upsertAccount(plaidAccount: PlaidAccount, plaidItemId: String): AccountEntity {
        val plaidId = plaidAccount.account_id
        val balance = BigDecimal(plaidAccount.balances.current ?: plaidAccount.balances.available ?: 0.0)
        val currency = plaidAccount.balances.iso_currency_code ?: "USD"
        val type = mapAccountType(plaidAccount.type, plaidAccount.subtype)
        val displayName = plaidAccount.official_name ?: plaidAccount.name

        val existingLink = db.plaidLinkDao().getAccountLink(plaidId)

        if (existingLink != null) {
            val account = db.accountDao().getById(existingLink.accountModelId)
            if (account != null) {
                val updatedAccount = account.copy(
                    name = displayName,
                    type = type,
                    balance = balance,
                    currencyCode = currency
                )
                db.accountDao().update(updatedAccount)
                db.plaidLinkDao().insertAccountLink(existingLink.copy(lastBalance = balance, updatedAt = Date()))
                appendSnapshotIfChanged(updatedAccount, balance)
                return updatedAccount
            }
        }

        val account = AccountEntity(
            name = displayName,
            type = type,
            balance = balance,
            currencyCode = currency
        )
        db.accountDao().insert(account)
        appendSnapshotIfChanged(account, balance)

        val link = PlaidAccountLinkEntity(
            plaidAccountId = plaidId,
            plaidItemId = plaidItemId,
            accountModelId = account.id,
            lastBalance = balance
        )
        db.plaidLinkDao().insertAccountLink(link)
        return account
    }

    private suspend fun appendSnapshotIfChanged(account: AccountEntity, balance: BigDecimal) {
        val snapshots = db.netWorthDao().getAllSnapshotsList()
        val lastForAccount = snapshots
            .filter { it.accountId == account.id }
            .maxByOrNull { it.date }
        if (lastForAccount?.balance != balance) {
            db.netWorthDao().insertSnapshot(
                BalanceSnapshotEntity(date = Date(), balance = balance, accountId = account.id)
            )
        }
    }

    // MARK: Transactions

    data class SyncTxResult(val added: Int, val modified: Int, val removed: Int)

    private suspend fun syncTransactions(item: StoredPlaidItem): SyncTxResult {
        val cursor = plaidStorage.getCursor(item.itemId)
        val response = plaidApi.syncTransactions(item.accessToken, SyncBody(cursor))

        for (tx in response.added) applyAdded(tx)
        for (tx in response.modified) applyModified(tx)
        for (removed in response.removed) applyRemoved(removed.transaction_id)

        if (response.nextCursor != null) {
            plaidStorage.setCursor(item.itemId, response.nextCursor)
        }

        return SyncTxResult(response.added.size, response.modified.size, response.removed.size)
    }

    private suspend fun applyAdded(tx: PlaidTransaction) {
        val existingLink = db.plaidLinkDao().getTransactionLink(tx.transaction_id)
        if (existingLink != null) {
            applyModified(tx, existingLink)
            return
        }

        val accountLink = db.plaidLinkDao().getAccountLink(tx.account_id) ?: return
        val account = db.accountDao().getById(accountLink.accountModelId) ?: return

        val date = parsePlaidDate(tx.date) ?: Date()
        val amount = BigDecimal(-tx.amount)
        val merchant = tx.merchant_name ?: tx.name

        val tempTx = TransactionEntity(
            date = date,
            amount = amount,
            merchant = merchant,
            memo = tx.personal_finance_category?.detailed,
            cleared = !tx.pending,
            flagColor = null,
            accountId = account.id,
            categoryId = null,
            pfcPrimary = tx.personal_finance_category?.primary
        )

        val rules = db.categoryRuleDao().getEnabledRules()
        val matchedCategoryId = RuleEngine.applyRules(rules, tempTx, db)
        val transaction = tempTx.copy(categoryId = matchedCategoryId)

        db.transactionDao().insert(transaction)

        val link = PlaidTransactionLinkEntity(
            plaidTransactionId = tx.transaction_id,
            transactionModelId = transaction.id,
            plaidAccountId = tx.account_id,
            pending = tx.pending
        )
        db.plaidLinkDao().insertTransactionLink(link)
    }

    private suspend fun applyModified(tx: PlaidTransaction, existingLink: PlaidTransactionLinkEntity? = null) {
        val link = existingLink ?: db.plaidLinkDao().getTransactionLink(tx.transaction_id) ?: return
        val transaction = db.transactionDao().getById(link.transactionModelId) ?: return
        val updated = transaction.copy(
            date = parsePlaidDate(tx.date) ?: transaction.date,
            amount = BigDecimal(-tx.amount),
            merchant = tx.merchant_name ?: tx.name,
            memo = tx.personal_finance_category?.detailed,
            pfcPrimary = tx.personal_finance_category?.primary,
            cleared = !tx.pending
        )
        db.transactionDao().update(updated)
        db.plaidLinkDao().insertTransactionLink(link.copy(pending = tx.pending))
    }

    private suspend fun applyRemoved(plaidTransactionId: String) {
        val link = db.plaidLinkDao().getTransactionLink(plaidTransactionId) ?: return
        val transaction = db.transactionDao().getById(link.transactionModelId)
        if (transaction != null) {
            db.transactionDao().delete(transaction)
        }
        db.plaidLinkDao().deleteTransactionLink(plaidTransactionId)
    }

    // MARK: Holdings

    suspend fun syncHoldings(item: StoredPlaidItem): Int {
        val response = try {
            plaidApi.getHoldings(item.accessToken)
        } catch (e: Exception) {
            if (isUnsupportedProduct(e)) return 0
            throw e
        }
        val securitiesById = response.securities.associateBy { it.security_id }
        var count = 0
        for (holding in response.holdings) {
            upsertHolding(holding, securitiesById[holding.security_id])
            count++
        }
        return count
    }

    private suspend fun upsertHolding(holding: PlaidHolding, security: PlaidSecurity?) {
        val key = "${holding.account_id}::${holding.security_id}"
        val quantity = BigDecimal(holding.quantity.toString())
        val price = BigDecimal(holding.institution_price.toString())
        val value = BigDecimal(holding.institution_value.toString())
        val costBasis = holding.cost_basis?.let { BigDecimal(it.toString()) }
        val currency = holding.iso_currency_code ?: "USD"
        val asOf = holding.institution_price_as_of?.let { parsePlaidDate(it) } ?: Date()
        val accountLink = db.plaidLinkDao().getAccountLink(holding.account_id)
        val accountId = accountLink?.accountModelId

        val existing = db.investmentDao().getAllHoldingsList().firstOrNull { it.plaidHoldingKey == key }
        if (existing != null) {
            db.investmentDao().insertHolding(
                existing.copy(
                    quantity = quantity,
                    institutionPrice = price,
                    institutionValue = value,
                    costBasis = costBasis,
                    currencyCode = currency,
                    asOfDate = asOf,
                    tickerSymbol = security?.ticker_symbol,
                    securityName = security?.name,
                    securityType = security?.type,
                    isCashEquivalent = security?.is_cash_equivalent ?: existing.isCashEquivalent,
                    accountId = accountId ?: existing.accountId
                )
            )
            return
        }

        db.investmentDao().insertHolding(
            InvestmentHoldingEntity(
                plaidAccountId = holding.account_id,
                plaidSecurityId = holding.security_id,
                plaidHoldingKey = key,
                tickerSymbol = security?.ticker_symbol,
                securityName = security?.name,
                securityType = security?.type,
                isCashEquivalent = security?.is_cash_equivalent ?: false,
                quantity = quantity,
                institutionPrice = price,
                institutionValue = value,
                costBasis = costBasis,
                currencyCode = currency,
                asOfDate = asOf,
                accountId = accountId
            )
        )
    }

    // MARK: Investment Transactions

    suspend fun syncInvestmentTransactions(item: StoredPlaidItem): Int {
        val response = try {
            plaidApi.getInvestmentTransactions(item.accessToken)
        } catch (e: Exception) {
            if (isUnsupportedProduct(e)) return 0
            throw e
        }
        val securitiesById = response.securities.associateBy { it.security_id }
        var count = 0
        for (tx in response.investment_transactions) {
            upsertInvestmentTransaction(tx, tx.security_id?.let { securitiesById[it] })
            count++
        }
        return count
    }

    private suspend fun upsertInvestmentTransaction(tx: PlaidInvestmentTransaction, security: PlaidSecurity?) {
        val accountLink = db.plaidLinkDao().getAccountLink(tx.account_id)
        val accountId = accountLink?.accountModelId

        val existing = db.investmentDao().getAllTransactionsList()
            .firstOrNull { it.plaidInvestmentTransactionId == tx.investment_transaction_id }
        if (existing != null) {
            db.investmentDao().insertTransaction(
                existing.copy(
                    date = parsePlaidDate(tx.date) ?: existing.date,
                    name = tx.name,
                    amount = BigDecimal(tx.amount.toString()),
                    fees = tx.fees?.let { BigDecimal(it.toString()) },
                    quantity = tx.quantity?.let { BigDecimal(it.toString()) },
                    price = tx.price?.let { BigDecimal(it.toString()) },
                    type = tx.type ?: existing.type,
                    subtype = tx.subtype,
                    plaidSecurityId = tx.security_id,
                    tickerSymbol = security?.ticker_symbol,
                    securityName = security?.name,
                    currencyCode = tx.iso_currency_code ?: existing.currencyCode,
                    accountId = accountId ?: existing.accountId
                )
            )
            return
        }

        db.investmentDao().insertTransaction(
            InvestmentTransactionEntity(
                plaidInvestmentTransactionId = tx.investment_transaction_id,
                date = parsePlaidDate(tx.date) ?: Date(),
                name = tx.name,
                amount = BigDecimal(tx.amount.toString()),
                fees = tx.fees?.let { BigDecimal(it.toString()) },
                quantity = tx.quantity?.let { BigDecimal(it.toString()) },
                price = tx.price?.let { BigDecimal(it.toString()) },
                type = tx.type ?: "unknown",
                subtype = tx.subtype,
                plaidSecurityId = tx.security_id,
                tickerSymbol = security?.ticker_symbol,
                securityName = security?.name,
                currencyCode = tx.iso_currency_code ?: "USD",
                accountId = accountId
            )
        )
    }

    // MARK: Liabilities

    suspend fun syncLiabilities(item: StoredPlaidItem): Int {
        val response = try {
            plaidApi.getLiabilities(item.accessToken)
        } catch (e: Exception) {
            if (isUnsupportedProduct(e)) return 0
            throw e
        }
        var count = 0
        for (credit in response.liabilities.credit ?: emptyList()) {
            val accountId = credit.account_id ?: continue
            upsertCreditLiability(credit, accountId)
            count++
        }
        for (mortgage in response.liabilities.mortgage ?: emptyList()) {
            val accountId = mortgage.account_id ?: continue
            upsertMortgageLiability(mortgage, accountId)
            count++
        }
        for (student in response.liabilities.student ?: emptyList()) {
            val accountId = student.account_id ?: continue
            upsertStudentLiability(student, accountId)
            count++
        }
        return count
    }

    private suspend fun upsertCreditLiability(credit: PlaidCreditLiability, accountId: String) {
        val purchaseApr = credit.aprs?.firstOrNull { it.apr_type?.lowercase()?.contains("purchase") == true }
            ?: credit.aprs?.firstOrNull()
        upsertLiability(
            accountId = accountId,
            kind = LiabilityKind.CREDIT,
            lastStatementBalance = credit.last_statement_balance?.let { BigDecimal(it.toString()) },
            lastStatementIssueDate = credit.last_statement_issue_date?.let { parsePlaidDate(it) },
            minimumPayment = credit.minimum_payment_amount?.let { BigDecimal(it.toString()) },
            nextPaymentDueDate = credit.next_payment_due_date?.let { parsePlaidDate(it) },
            lastPaymentAmount = credit.last_payment_amount?.let { BigDecimal(it.toString()) },
            lastPaymentDate = credit.last_payment_date?.let { parsePlaidDate(it) },
            interestRatePercentage = purchaseApr?.apr_percentage?.let { BigDecimal(it.toString()) },
            originationPrincipal = null,
            originationDate = null,
            maturityDate = null,
            loanName = purchaseApr?.apr_type,
            rawJSON = encodeRaw(credit)
        )
    }

    private suspend fun upsertMortgageLiability(mortgage: PlaidMortgageLiability, accountId: String) {
        upsertLiability(
            accountId = accountId,
            kind = LiabilityKind.MORTGAGE,
            lastStatementBalance = null,
            lastStatementIssueDate = null,
            minimumPayment = mortgage.next_monthly_payment?.let { BigDecimal(it.toString()) },
            nextPaymentDueDate = mortgage.next_payment_due_date?.let { parsePlaidDate(it) },
            lastPaymentAmount = mortgage.last_payment_amount?.let { BigDecimal(it.toString()) },
            lastPaymentDate = mortgage.last_payment_date?.let { parsePlaidDate(it) },
            interestRatePercentage = mortgage.interest_rate?.percentage?.let { BigDecimal(it.toString()) },
            originationPrincipal = mortgage.origination_principal_amount?.let { BigDecimal(it.toString()) },
            originationDate = mortgage.origination_date?.let { parsePlaidDate(it) },
            maturityDate = mortgage.maturity_date?.let { parsePlaidDate(it) },
            loanName = mortgage.loan_type_description ?: mortgage.loan_term,
            rawJSON = encodeRaw(mortgage)
        )
    }

    private suspend fun upsertStudentLiability(student: PlaidStudentLiability, accountId: String) {
        upsertLiability(
            accountId = accountId,
            kind = LiabilityKind.STUDENT,
            lastStatementBalance = student.last_statement_balance?.let { BigDecimal(it.toString()) },
            lastStatementIssueDate = student.last_statement_issue_date?.let { parsePlaidDate(it) },
            minimumPayment = student.minimum_payment_amount?.let { BigDecimal(it.toString()) },
            nextPaymentDueDate = student.next_payment_due_date?.let { parsePlaidDate(it) },
            lastPaymentAmount = student.last_payment_amount?.let { BigDecimal(it.toString()) },
            lastPaymentDate = student.last_payment_date?.let { parsePlaidDate(it) },
            interestRatePercentage = student.interest_rate_percentage?.let { BigDecimal(it.toString()) },
            originationPrincipal = student.origination_principal_amount?.let { BigDecimal(it.toString()) },
            originationDate = student.origination_date?.let { parsePlaidDate(it) },
            maturityDate = null,
            loanName = student.loan_name,
            rawJSON = encodeRaw(student)
        )
    }

    private suspend fun upsertLiability(
        accountId: String,
        kind: LiabilityKind,
        lastStatementBalance: BigDecimal?,
        lastStatementIssueDate: Date?,
        minimumPayment: BigDecimal?,
        nextPaymentDueDate: Date?,
        lastPaymentAmount: BigDecimal?,
        lastPaymentDate: Date?,
        interestRatePercentage: BigDecimal?,
        originationPrincipal: BigDecimal?,
        originationDate: Date?,
        maturityDate: Date?,
        loanName: String?,
        rawJSON: String?
    ) {
        val accountLink = db.plaidLinkDao().getAccountLink(accountId)
        val linkedAccountId = accountLink?.accountModelId

        val existing = db.liabilityDao().getAllList().firstOrNull { it.plaidAccountId == accountId }
        if (existing != null) {
            db.liabilityDao().update(
                existing.copy(
                    kind = kind,
                    lastStatementBalance = lastStatementBalance,
                    lastStatementIssueDate = lastStatementIssueDate,
                    minimumPayment = minimumPayment,
                    nextPaymentDueDate = nextPaymentDueDate,
                    lastPaymentAmount = lastPaymentAmount,
                    lastPaymentDate = lastPaymentDate,
                    interestRatePercentage = interestRatePercentage,
                    originationPrincipal = originationPrincipal,
                    originationDate = originationDate,
                    maturityDate = maturityDate,
                    loanName = loanName,
                    rawJSON = rawJSON,
                    updatedAt = Date(),
                    accountId = linkedAccountId ?: existing.accountId
                )
            )
            return
        }

        db.liabilityDao().insert(
            LiabilityEntity(
                plaidAccountId = accountId,
                kind = kind,
                lastStatementBalance = lastStatementBalance,
                lastStatementIssueDate = lastStatementIssueDate,
                minimumPayment = minimumPayment,
                nextPaymentDueDate = nextPaymentDueDate,
                lastPaymentAmount = lastPaymentAmount,
                lastPaymentDate = lastPaymentDate,
                interestRatePercentage = interestRatePercentage,
                originationPrincipal = originationPrincipal,
                originationDate = originationDate,
                maturityDate = maturityDate,
                loanName = loanName,
                rawJSON = rawJSON,
                updatedAt = Date(),
                accountId = linkedAccountId
            )
        )
    }

    // MARK: Helpers

    private fun mapAccountType(plaidType: String, subtype: String?): AccountType {
        return when (plaidType.lowercase()) {
            "depository" -> when (subtype?.lowercase()) {
                "savings", "money market", "cd" -> AccountType.SAVINGS
                else -> AccountType.CHECKING
            }
            "credit" -> AccountType.CREDIT_CARD
            "loan" -> AccountType.LOAN
            "investment" -> if (subtype?.lowercase() == "retirement" || subtype?.lowercase()?.contains("401k") == true)
                AccountType.RETIREMENT else AccountType.INVESTMENT
            else -> AccountType.MANUAL_ASSET
        }
    }

    private fun parsePlaidDate(value: String): Date? {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)
        } catch (e: Exception) {
            null
        }
    }

    private fun encodeRaw(value: Any): String? {
        return try { value.toString() } catch (_: Exception) { null }
    }

    private fun isUnsupportedProduct(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: return false
        return msg.contains("products_not_supported") ||
            msg.contains("product_not_ready") ||
            msg.contains("invalid_product") ||
            msg.contains("no_investment_accounts") ||
            msg.contains("no_liability_accounts")
    }
}
