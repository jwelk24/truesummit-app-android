package com.truesummit.android.service

import android.content.Context
import com.truesummit.android.billing.PremiumManager
import com.truesummit.android.billing.SubscriptionTier
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.*
import com.truesummit.android.data.model.AccountType
import com.truesummit.android.data.model.GoalType
import com.truesummit.android.data.model.LiabilityKind
import com.truesummit.android.data.model.ScheduledKind
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@Serializable
private data class AccountRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    val name: String,
    val type: String,
    val balance: Double,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class CategoryGroupRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    val name: String,
    val sort: Int,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class CategoryRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("group_id") val groupId: String?,
    @SerialName("linked_account_id") val linkedAccountId: String?,
    val name: String,
    val sort: Int,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class TransactionRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("account_id") val accountId: String?,
    @SerialName("category_id") val categoryId: String?,
    val date: String,
    val amount: Double,
    val merchant: String,
    val memo: String?,
    val cleared: Boolean,
    @SerialName("flag_color") val flagColor: String?,
    @SerialName("pfc_primary") val pfcPrimary: String? = null,
    val tags: String = "",
    @SerialName("awaiting_refund") val awaitingRefund: Boolean = false,
    @SerialName("refunds_transaction_id") val refundsTransactionId: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class TransactionSplitRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("category_id") val categoryId: String?,
    val amount: Double,
    val memo: String?,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class GoalRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("category_id") val categoryId: String?,
    val type: String,
    @SerialName("target_amount") val targetAmount: Double,
    @SerialName("target_date") val targetDate: String?,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class BudgetMonthRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    val year: Int,
    val month: Int,
    val carryover: Double,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class BudgetAllocationRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("month_id") val monthId: String,
    @SerialName("category_id") val categoryId: String,
    val amount: Double,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class ScheduledItemRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("account_id") val accountId: String?,
    @SerialName("category_id") val categoryId: String?,
    val kind: String,
    val name: String,
    val amount: Double,
    @SerialName("next_date") val nextDate: String,
    @SerialName("interval_days") val intervalDays: Int,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class CategoryRuleRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    val priority: Int,
    @SerialName("match_field") val matchField: String,
    @SerialName("match_kind") val matchKind: String,
    val pattern: String,
    @SerialName("case_sensitive") val caseSensitive: Boolean,
    val enabled: Boolean,
    @SerialName("category_id") val categoryId: String?,
    @SerialName("rename_to") val renameTo: String? = null,
    @SerialName("add_tags") val addTags: String = "",
    @SerialName("times_applied") val timesApplied: Int,
    @SerialName("last_applied_at") val lastAppliedAt: String?,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class BalanceSnapshotRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("account_id") val accountId: String,
    val date: String,
    val balance: Double,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class LiabilityRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("account_id") val accountId: String?,
    @SerialName("plaid_account_id") val plaidAccountId: String,
    val kind: String,
    @SerialName("last_statement_balance") val lastStatementBalance: Double?,
    @SerialName("last_statement_issue_date") val lastStatementIssueDate: String?,
    @SerialName("minimum_payment") val minimumPayment: Double?,
    @SerialName("next_payment_due_date") val nextPaymentDueDate: String?,
    @SerialName("last_payment_amount") val lastPaymentAmount: Double?,
    @SerialName("last_payment_date") val lastPaymentDate: String?,
    @SerialName("interest_rate_percentage") val interestRatePercentage: Double?,
    @SerialName("origination_principal") val originationPrincipal: Double?,
    @SerialName("origination_date") val originationDate: String?,
    @SerialName("maturity_date") val maturityDate: String?,
    @SerialName("loan_name") val loanName: String?,
    @SerialName("raw_json") val rawJson: String?,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class InvestmentHoldingRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("account_id") val accountId: String?,
    @SerialName("plaid_account_id") val plaidAccountId: String,
    @SerialName("plaid_security_id") val plaidSecurityId: String,
    @SerialName("ticker_symbol") val tickerSymbol: String?,
    @SerialName("security_name") val securityName: String?,
    @SerialName("security_type") val securityType: String?,
    @SerialName("is_cash_equivalent") val isCashEquivalent: Boolean,
    val quantity: Double,
    @SerialName("institution_price") val institutionPrice: Double,
    @SerialName("institution_value") val institutionValue: Double,
    @SerialName("cost_basis") val costBasis: Double?,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("as_of_date") val asOfDate: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class InvestmentTransactionRow(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("account_id") val accountId: String?,
    @SerialName("plaid_investment_transaction_id") val plaidInvestmentTransactionId: String,
    val date: String,
    val name: String,
    val amount: Double,
    val fees: Double?,
    val quantity: Double?,
    val price: Double?,
    val type: String,
    val subtype: String?,
    @SerialName("plaid_security_id") val plaidSecurityId: String?,
    @SerialName("ticker_symbol") val tickerSymbol: String?,
    @SerialName("security_name") val securityName: String?,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class PlaidAccountLinkRow(
    @SerialName("household_id") val householdId: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("plaid_item_id") val plaidItemId: String,
    @SerialName("plaid_account_id") val plaidAccountId: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class PlaidTransactionLinkRow(
    @SerialName("household_id") val householdId: String,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("plaid_transaction_id") val plaidTransactionId: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class DeletedAtUpdate(
    @SerialName("deleted_at") val deletedAt: String
)

object SyncService {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private var lastSyncedAt: Date? = null
    private val throttleIntervalMs: Long = 15_000L

    private val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun syncIfDue(context: Context) {
        val last = lastSyncedAt
        if (last != null && (Date().time - last.time) < throttleIntervalMs) return
        syncAll(context)
    }

    suspend fun syncAll(context: Context) {
        val tier = PremiumManager.currentTier.value
        if (tier == SubscriptionTier.NONE) return

        val household = HouseholdService.currentHousehold.value ?: return
        _isSyncing.value = true
        try {
            val db = AppDatabase.getInstance(context.applicationContext)
            val householdIdStr = household.id.toString().lowercase()
            val canWrite = HouseholdService.currentRole.value?.canWrite ?: false

            if (canWrite) {
                deduplicateAccounts(db)
                deduplicateCategoriesAndGroups(db)
                pushAccounts(db, householdIdStr)
                pushCategoryGroups(db, householdIdStr)
                pushCategories(db, householdIdStr)
                pushGoals(db, householdIdStr)
                reconcileLocalBudgetMonths(db, household.id)
                pushBudgetMonths(db, householdIdStr)
                pushBudgetAllocations(db, householdIdStr)
                pushScheduledItems(db, householdIdStr)
                pushTransactions(db, householdIdStr)
                pushTransactionSplits(db, householdIdStr)
                pushBalanceSnapshots(db, householdIdStr)
                pushLiabilities(db, householdIdStr)
                pushInvestmentHoldings(db, householdIdStr)
                pushInvestmentTransactions(db, householdIdStr)
                pushPlaidAccountLinks(db, householdIdStr)
                pushPlaidTransactionLinks(db, householdIdStr)
                pushCategoryRules(db, householdIdStr)
                pushDeletions(db, householdIdStr)
            }

            pullAccounts(db, householdIdStr)
            pullCategoryGroups(db, householdIdStr)
            pullCategories(db, householdIdStr)
            pullGoals(db, householdIdStr)
            pullBudgetMonths(db, householdIdStr)
            pullBudgetAllocations(db, householdIdStr)
            pullScheduledItems(db, householdIdStr)
            pullTransactions(db, householdIdStr)
            pullTransactionSplits(db, householdIdStr)
            pullBalanceSnapshots(db, householdIdStr)
            pullLiabilities(db, householdIdStr)
            pullInvestmentHoldings(db, householdIdStr)
            pullInvestmentTransactions(db, householdIdStr)
            pullPlaidAccountLinks(db, householdIdStr)
            pullPlaidTransactionLinks(db, householdIdStr)
            pullCategoryRules(db, householdIdStr)

            lastSyncedAt = Date()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSyncing.value = false
        }
    }

    // MARK: - Deduplication (seed-collision fix)

    /// Merges duplicate AccountEntity records created when a fresh-install seed
    /// collides with the household's existing accounts. Plaid-linked accounts are
    /// keyed by plaidAccountId; manual accounts by name+type. Keeps the smallest-UUID
    /// survivor, re-points all children, then tombstones + deletes the rest.
    private suspend fun deduplicateAccounts(db: AppDatabase) {
        val accounts = db.accountDao().getAll().first()
        if (accounts.size <= 1) return

        val links = db.plaidLinkDao().getAllAccountLinks()
        val plaidIdByAccount = links.associate { it.accountModelId to it.plaidAccountId }

        val byKey = accounts.groupBy { acct ->
            val plaidId = plaidIdByAccount[acct.id]
            if (plaidId != null) "plaid|$plaidId" else "manual|${acct.name}|${acct.type.name}"
        }
        if (byKey.none { it.value.size > 1 }) return

        val allTransactions = db.transactionDao().getAll().first()
        val allSnapshots = db.netWorthDao().getAllSnapshotsList()

        for ((_, dupes) in byKey) {
            if (dupes.size <= 1) continue
            val sorted = dupes.sortedBy { it.id.toString() }
            val survivor = sorted[0]
            for (dup in sorted.drop(1)) {
                val dupId = dup.id
                // Re-point transactions
                for (tx in allTransactions.filter { it.accountId == dupId }) {
                    db.transactionDao().update(tx.copy(accountId = survivor.id))
                }
                // Re-point balance snapshots (REPLACE by PK, then CASCADE on dup delete won't fire)
                for (snap in allSnapshots.filter { it.accountId == dupId }) {
                    db.netWorthDao().insertSnapshot(snap.copy(accountId = survivor.id))
                }
                // Scheduled items use SET_NULL on account delete, no re-pointing needed
                // Re-point plaid account links
                for (link in links.filter { it.accountModelId == dupId }) {
                    db.plaidLinkDao().insertAccountLink(link.copy(accountModelId = survivor.id))
                }
                db.softDeleteTombstoneDao().insert(SoftDeleteTombstoneEntity(table = "accounts", recordID = dupId))
                db.accountDao().delete(dup)
            }
        }
    }

    /// Merges duplicate CategoryGroupEntity records (by name) and CategoryEntity
    /// records (by group+name) from fresh-install seeds. Keeps the smallest-UUID
    /// survivor, re-points children, then tombstones + deletes duplicates.
    private suspend fun deduplicateCategoriesAndGroups(db: AppDatabase) {
        // Step 1: dedupe groups by name
        val groups = db.categoryDao().getGroupsList()
        val groupsByName = groups.groupBy { it.name }
        val groupRemap = mutableMapOf<UUID, UUID>() // dup.id -> survivor.id
        for ((_, dupes) in groupsByName) {
            if (dupes.size <= 1) continue
            val sorted = dupes.sortedBy { it.id.toString() }
            val survivor = sorted[0]
            for (dup in sorted.drop(1)) {
                groupRemap[dup.id] = survivor.id
                db.softDeleteTombstoneDao().insert(SoftDeleteTombstoneEntity(table = "category_groups", recordID = dup.id))
                db.categoryDao().deleteGroup(dup)
            }
        }

        // Step 2: re-point categories whose group was merged, then dedupe by (groupId, name)
        val allCategories = db.categoryDao().getCategoriesList()
        for (cat in allCategories) {
            val newGroupId = groupRemap[cat.groupId] ?: continue
            db.categoryDao().insertCategory(cat.copy(groupId = newGroupId))
        }

        val refreshedCats = db.categoryDao().getCategoriesList()
        val catsByKey = refreshedCats.groupBy { cat -> "${cat.groupId}|${cat.name}" }
        for ((_, dupes) in catsByKey) {
            if (dupes.size <= 1) continue
            val sorted = dupes.sortedBy { it.id.toString() }
            val survivor = sorted[0]
            for (dup in sorted.drop(1)) {
                val dupId = dup.id
                // Re-point transactions
                val txns = db.transactionDao().getAll().first()
                for (tx in txns.filter { it.categoryId == dupId }) {
                    db.transactionDao().update(tx.copy(categoryId = survivor.id))
                }
                // Re-point splits
                val splits = db.transactionDao().getAllSplits()
                for (split in splits.filter { it.categoryId == dupId }) {
                    db.transactionDao().updateSplit(split.copy(categoryId = survivor.id))
                }
                db.softDeleteTombstoneDao().insert(SoftDeleteTombstoneEntity(table = "categories", recordID = dupId))
                db.categoryDao().deleteCategory(dup)
            }
        }
    }

    // MARK: - Accounts

    private suspend fun pushAccounts(db: AppDatabase, householdId: String) {
        val rows = db.accountDao().getAll().first().map { a ->
            AccountRow(a.id.toString(), householdId, a.name, a.type.name.lowercase(), a.balance.toDouble(), a.currencyCode)
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["accounts"].upsert(rows)
    }

    private suspend fun pullAccounts(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["accounts"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<AccountRow>()
        for (row in remote) {
            val type = try { AccountType.valueOf(row.type.uppercase()) } catch (e: Exception) { AccountType.CHECKING }
            val entity = AccountEntity(id = UUID.fromString(row.id), name = row.name, type = type, balance = BigDecimal.valueOf(row.balance), currencyCode = row.currencyCode)
            val local = db.accountDao().getById(entity.id)
            if (local != null) db.accountDao().update(entity) else db.accountDao().insert(entity)
        }
    }

    // MARK: - Category Groups

    private suspend fun pushCategoryGroups(db: AppDatabase, householdId: String) {
        val rows = db.categoryDao().getGroups().first().map { g ->
            CategoryGroupRow(g.id.toString(), householdId, g.name, g.sort)
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["category_groups"].upsert(rows)
    }

    private suspend fun pullCategoryGroups(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["category_groups"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<CategoryGroupRow>()
        for (row in remote) {
            db.categoryDao().insertGroup(CategoryGroupEntity(UUID.fromString(row.id), row.name, row.sort))
        }
    }

    // MARK: - Categories

    private suspend fun pushCategories(db: AppDatabase, householdId: String) {
        val rows = db.categoryDao().getCategories().first().map { c ->
            CategoryRow(c.id.toString(), householdId, c.groupId?.toString(), c.linkedAccountId?.toString(), c.name, c.sort)
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["categories"].upsert(rows)
    }

    private suspend fun pullCategories(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["categories"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<CategoryRow>()
        for (row in remote) {
            val catId = UUID.fromString(row.id)
            val groupId = row.groupId?.let { UUID.fromString(it) }
            val linkedId = row.linkedAccountId?.let { UUID.fromString(it) }
            db.categoryDao().insertCategory(CategoryEntity(catId, row.name, row.sort, groupId, linkedId))
        }
    }

    // MARK: - Goals

    private suspend fun pushGoals(db: AppDatabase, householdId: String) {
        val rows = db.goalDao().getAllGoals().first().map { g ->
            GoalRow(g.id.toString(), householdId, g.categoryId.toString(), g.type.name.lowercase(), g.targetAmount.toDouble(), g.targetDate?.let { df.format(it) })
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["goals"].upsert(rows)
    }

    private suspend fun pullGoals(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["goals"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<GoalRow>()
        for (row in remote) {
            val type = try { GoalType.valueOf(row.type.uppercase()) } catch (e: Exception) { GoalType.MONTHLY_AMOUNT }
            val catId = row.categoryId?.let { UUID.fromString(it) }
            val targetDate = row.targetDate?.let { try { df.parse(it) } catch (e: Exception) { null } }
            db.goalDao().insert(GoalEntity(UUID.fromString(row.id), type, BigDecimal.valueOf(row.targetAmount), targetDate, catId))
        }
    }

    // MARK: - Budget Months

    private suspend fun reconcileLocalBudgetMonths(db: AppDatabase, householdID: java.util.UUID) {
        try {
            val remote = SupabaseService.client.postgrest["budget_months"].select {
                filter {
                    filter("household_id", FilterOperator.EQ, householdID.toString().lowercase())
                    filter("deleted_at", FilterOperator.IS, "null")
                }
            }.decodeList<BudgetMonthRow>()
            if (remote.isEmpty()) return

            val serverByKey = remote.associate { "${it.year}-${it.month}" to it.id }
            val local = db.budgetDao().getAllMonths()
            for (m in local) {
                val serverIdStr = serverByKey["${m.year}-${m.month}"] ?: continue
                val serverId = java.util.UUID.fromString(serverIdStr)
                if (serverId != m.id) {
                    db.budgetDao().deleteMonth(m)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun pushBudgetMonths(db: AppDatabase, householdId: String) {
        val rows = db.budgetDao().getAllMonths().map { m ->
            BudgetMonthRow(m.id.toString(), householdId, m.year, m.month, m.carryover.toDouble())
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["budget_months"].upsert(rows)
    }

    private suspend fun pullBudgetMonths(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["budget_months"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<BudgetMonthRow>()
        for (row in remote) {
            db.budgetDao().insertMonth(BudgetMonthEntity(UUID.fromString(row.id), row.year, row.month, BigDecimal.valueOf(row.carryover)))
        }
    }

    // MARK: - Budget Allocations

    private suspend fun pushBudgetAllocations(db: AppDatabase, householdId: String) {
        val rows = db.budgetDao().getAllAllocations().map { a ->
            BudgetAllocationRow(a.id.toString(), householdId, a.monthId.toString(), a.categoryId.toString(), a.amount.toDouble())
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["budget_allocations"].upsert(rows)
    }

    private suspend fun pullBudgetAllocations(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["budget_allocations"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<BudgetAllocationRow>()
        for (row in remote) {
            db.budgetDao().insertAllocation(BudgetAllocationEntity(UUID.fromString(row.id), BigDecimal.valueOf(row.amount), UUID.fromString(row.categoryId), UUID.fromString(row.monthId)))
        }
    }

    // MARK: - Scheduled Items

    private suspend fun pushScheduledItems(db: AppDatabase, householdId: String) {
        val rows = db.scheduledItemDao().getAll().first().map { s ->
            ScheduledItemRow(s.id.toString(), householdId, s.accountId?.toString(), s.categoryId?.toString(), s.kind.name.lowercase(), s.name, s.amount.toDouble(), df.format(s.nextDate), s.intervalDays)
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["scheduled_items"].upsert(rows)
    }

    private suspend fun pullScheduledItems(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["scheduled_items"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<ScheduledItemRow>()
        for (row in remote) {
            val kind = try { ScheduledKind.valueOf(row.kind.uppercase()) } catch (e: Exception) { ScheduledKind.BILL }
            val accId = row.accountId?.let { UUID.fromString(it) }
            val catId = row.categoryId?.let { UUID.fromString(it) }
            val nextDate = try { df.parse(row.nextDate) ?: Date() } catch (e: Exception) { Date() }
            db.scheduledItemDao().insert(ScheduledItemEntity(UUID.fromString(row.id), kind, row.name, BigDecimal.valueOf(row.amount), nextDate, row.intervalDays, accId, catId))
        }
    }

    // MARK: - Transactions

    private suspend fun pushTransactions(db: AppDatabase, householdId: String) {
        val rows = db.transactionDao().getAll().first().map { t ->
            TransactionRow(t.id.toString(), householdId, t.accountId?.toString(), t.categoryId?.toString(), df.format(t.date), t.amount.toDouble(), t.merchant, t.memo, t.cleared, t.flagColor, t.pfcPrimary, t.tags, t.awaitingRefund, t.refundsTransactionId?.toString())
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["transactions"].upsert(rows)
    }

    private suspend fun pullTransactions(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["transactions"].select {
            filter { filter("household_id", FilterOperator.EQ, householdId) }
        }.decodeList<TransactionRow>()
        for (row in remote) {
            val txId = UUID.fromString(row.id)
            val localTx = db.transactionDao().getById(txId)
            if (row.deletedAt != null) {
                if (localTx != null) db.transactionDao().delete(localTx)
                continue
            }
            val accId = row.accountId?.let { UUID.fromString(it) }
            val catId = row.categoryId?.let { UUID.fromString(it) }
            val date = try { df.parse(row.date) ?: Date() } catch (e: Exception) { Date() }
            val refundsTxId = row.refundsTransactionId?.let { UUID.fromString(it) }
            val entity = TransactionEntity(txId, date, BigDecimal.valueOf(row.amount), row.merchant, row.memo, row.cleared, row.flagColor, row.pfcPrimary, accId, catId, row.tags, row.awaitingRefund, refundsTxId)
            db.transactionDao().insert(entity)
        }
    }

    // MARK: - Transaction Splits

    private suspend fun pushTransactionSplits(db: AppDatabase, householdId: String) {
        val rows = db.transactionDao().getAllSplits().map { s ->
            TransactionSplitRow(s.id.toString(), householdId, s.transactionId.toString(), s.categoryId?.toString(), s.amount.toDouble(), s.memo)
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["transaction_splits"].upsert(rows)
    }

    private suspend fun pullTransactionSplits(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["transaction_splits"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<TransactionSplitRow>()
        for (row in remote) {
            val txId = row.transactionId.let { UUID.fromString(it) }
            val catId = row.categoryId?.let { UUID.fromString(it) }
            db.transactionDao().updateSplit(TransactionSplitEntity(UUID.fromString(row.id), BigDecimal.valueOf(row.amount), row.memo, txId, catId))
        }
    }

    // MARK: - Balance Snapshots

    private suspend fun pushBalanceSnapshots(db: AppDatabase, householdId: String) {
        val rows = db.netWorthDao().getAllSnapshotsList().mapNotNull { s ->
            val accId = s.accountId ?: return@mapNotNull null
            BalanceSnapshotRow(s.id.toString(), householdId, accId.toString(), df.format(s.date), s.balance.toDouble())
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["balance_snapshots"].upsert(rows)
    }

    private suspend fun pullBalanceSnapshots(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["balance_snapshots"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<BalanceSnapshotRow>()
        for (row in remote) {
            val date = try { df.parse(row.date) ?: Date() } catch (e: Exception) { Date() }
            db.netWorthDao().insertSnapshot(BalanceSnapshotEntity(UUID.fromString(row.id), date, BigDecimal.valueOf(row.balance), UUID.fromString(row.accountId)))
        }
    }

    // MARK: - Liabilities

    private suspend fun pushLiabilities(db: AppDatabase, householdId: String) {
        val rows = db.liabilityDao().getAllList().map { l ->
            LiabilityRow(
                id = l.id.toString(), householdId = householdId,
                accountId = l.accountId?.toString(),
                plaidAccountId = l.plaidAccountId,
                kind = l.kind.name.lowercase(),
                lastStatementBalance = l.lastStatementBalance?.toDouble(),
                lastStatementIssueDate = l.lastStatementIssueDate?.let { df.format(it) },
                minimumPayment = l.minimumPayment?.toDouble(),
                nextPaymentDueDate = l.nextPaymentDueDate?.let { df.format(it) },
                lastPaymentAmount = l.lastPaymentAmount?.toDouble(),
                lastPaymentDate = l.lastPaymentDate?.let { df.format(it) },
                interestRatePercentage = l.interestRatePercentage?.toDouble(),
                originationPrincipal = l.originationPrincipal?.toDouble(),
                originationDate = l.originationDate?.let { df.format(it) },
                maturityDate = l.maturityDate?.let { df.format(it) },
                loanName = l.loanName,
                rawJson = l.rawJSON
            )
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["liabilities"].upsert(rows)
    }

    private suspend fun pullLiabilities(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["liabilities"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<LiabilityRow>()
        for (row in remote) {
            val kind = try { LiabilityKind.valueOf(row.kind.uppercase()) } catch (e: Exception) { continue }
            fun parseDate(s: String?) = s?.let { try { df.parse(it) } catch (e: Exception) { null } }
            val entity = LiabilityEntity(
                id = UUID.fromString(row.id),
                plaidAccountId = row.plaidAccountId,
                kind = kind,
                lastStatementBalance = row.lastStatementBalance?.let { BigDecimal.valueOf(it) },
                lastStatementIssueDate = parseDate(row.lastStatementIssueDate),
                minimumPayment = row.minimumPayment?.let { BigDecimal.valueOf(it) },
                nextPaymentDueDate = parseDate(row.nextPaymentDueDate),
                lastPaymentAmount = row.lastPaymentAmount?.let { BigDecimal.valueOf(it) },
                lastPaymentDate = parseDate(row.lastPaymentDate),
                interestRatePercentage = row.interestRatePercentage?.let { BigDecimal.valueOf(it) },
                originationPrincipal = row.originationPrincipal?.let { BigDecimal.valueOf(it) },
                originationDate = parseDate(row.originationDate),
                maturityDate = parseDate(row.maturityDate),
                loanName = row.loanName,
                rawJSON = row.rawJson,
                updatedAt = Date(),
                accountId = row.accountId?.let { UUID.fromString(it) }
            )
            db.liabilityDao().insert(entity)
        }
    }

    // MARK: - Investment Holdings

    private suspend fun pushInvestmentHoldings(db: AppDatabase, householdId: String) {
        val rows = db.investmentDao().getAllHoldingsList().map { h ->
            InvestmentHoldingRow(
                id = h.id.toString(), householdId = householdId,
                accountId = h.accountId?.toString(),
                plaidAccountId = h.plaidAccountId,
                plaidSecurityId = h.plaidSecurityId,
                tickerSymbol = h.tickerSymbol, securityName = h.securityName, securityType = h.securityType,
                isCashEquivalent = h.isCashEquivalent,
                quantity = h.quantity.toDouble(), institutionPrice = h.institutionPrice.toDouble(),
                institutionValue = h.institutionValue.toDouble(), costBasis = h.costBasis?.toDouble(),
                currencyCode = h.currencyCode, asOfDate = df.format(h.asOfDate)
            )
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["investment_holdings"].upsert(rows)
    }

    private suspend fun pullInvestmentHoldings(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["investment_holdings"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<InvestmentHoldingRow>()
        for (row in remote) {
            val asOfDate = try { df.parse(row.asOfDate) ?: Date() } catch (e: Exception) { Date() }
            val entity = InvestmentHoldingEntity(
                id = UUID.fromString(row.id),
                plaidHoldingKey = "${row.plaidAccountId}:${row.plaidSecurityId}",
                plaidAccountId = row.plaidAccountId, plaidSecurityId = row.plaidSecurityId,
                tickerSymbol = row.tickerSymbol, securityName = row.securityName, securityType = row.securityType,
                isCashEquivalent = row.isCashEquivalent,
                quantity = BigDecimal.valueOf(row.quantity),
                institutionPrice = BigDecimal.valueOf(row.institutionPrice),
                institutionValue = BigDecimal.valueOf(row.institutionValue),
                costBasis = row.costBasis?.let { BigDecimal.valueOf(it) },
                currencyCode = row.currencyCode, asOfDate = asOfDate,
                accountId = row.accountId?.let { UUID.fromString(it) }
            )
            db.investmentDao().insertHolding(entity)
        }
    }

    // MARK: - Investment Transactions

    private suspend fun pushInvestmentTransactions(db: AppDatabase, householdId: String) {
        val rows = db.investmentDao().getAllTransactionsList().map { t ->
            InvestmentTransactionRow(
                id = t.id.toString(), householdId = householdId,
                accountId = t.accountId?.toString(),
                plaidInvestmentTransactionId = t.plaidInvestmentTransactionId,
                date = df.format(t.date), name = t.name, amount = t.amount.toDouble(),
                fees = t.fees?.toDouble(), quantity = t.quantity?.toDouble(), price = t.price?.toDouble(),
                type = t.type, subtype = t.subtype, plaidSecurityId = t.plaidSecurityId,
                tickerSymbol = t.tickerSymbol, securityName = t.securityName, currencyCode = t.currencyCode
            )
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["investment_transactions"].upsert(rows)
    }

    private suspend fun pullInvestmentTransactions(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["investment_transactions"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<InvestmentTransactionRow>()
        for (row in remote) {
            val date = try { df.parse(row.date) ?: Date() } catch (e: Exception) { Date() }
            val entity = InvestmentTransactionEntity(
                id = UUID.fromString(row.id),
                plaidInvestmentTransactionId = row.plaidInvestmentTransactionId,
                date = date, name = row.name, amount = BigDecimal.valueOf(row.amount),
                fees = row.fees?.let { BigDecimal.valueOf(it) },
                quantity = row.quantity?.let { BigDecimal.valueOf(it) },
                price = row.price?.let { BigDecimal.valueOf(it) },
                type = row.type, subtype = row.subtype, plaidSecurityId = row.plaidSecurityId,
                tickerSymbol = row.tickerSymbol, securityName = row.securityName,
                currencyCode = row.currencyCode,
                accountId = row.accountId?.let { UUID.fromString(it) }
            )
            db.investmentDao().insertTransaction(entity)
        }
    }

    // MARK: - Plaid Account Links

    private suspend fun pushPlaidAccountLinks(db: AppDatabase, householdId: String) {
        val rows = db.plaidLinkDao().getAllAccountLinks().map { l ->
            PlaidAccountLinkRow(householdId, l.accountModelId.toString(), l.plaidItemId, l.plaidAccountId)
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["plaid_account_links"].upsert(rows)
    }

    private suspend fun pullPlaidAccountLinks(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["plaid_account_links"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<PlaidAccountLinkRow>()
        for (row in remote) {
            val existing = db.plaidLinkDao().getAccountLink(row.plaidAccountId)
            if (existing == null) {
                db.plaidLinkDao().insertAccountLink(PlaidAccountLinkEntity(
                    plaidAccountId = row.plaidAccountId, plaidItemId = row.plaidItemId,
                    accountModelId = UUID.fromString(row.accountId), lastBalance = BigDecimal.ZERO
                ))
            }
        }
    }

    // MARK: - Plaid Transaction Links

    private suspend fun pushPlaidTransactionLinks(db: AppDatabase, householdId: String) {
        val rows = db.plaidLinkDao().getAllTransactionLinks().map { l ->
            PlaidTransactionLinkRow(householdId, l.transactionModelId.toString(), l.plaidTransactionId)
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["plaid_transaction_links"].upsert(rows)
    }

    private suspend fun pullPlaidTransactionLinks(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["plaid_transaction_links"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<PlaidTransactionLinkRow>()
        for (row in remote) {
            val existing = db.plaidLinkDao().getTransactionLink(row.plaidTransactionId)
            if (existing == null) {
                db.plaidLinkDao().insertTransactionLink(PlaidTransactionLinkEntity(
                    plaidTransactionId = row.plaidTransactionId,
                    transactionModelId = UUID.fromString(row.transactionId),
                    plaidAccountId = "", pending = false
                ))
            }
        }
    }

    // MARK: - Category Rules

    private suspend fun pushCategoryRules(db: AppDatabase, householdId: String) {
        val rows = db.categoryRuleDao().getAll().first().map { r ->
            CategoryRuleRow(
                id = r.id.toString(), householdId = householdId,
                priority = r.priority, matchField = r.matchField, matchKind = r.matchKind,
                pattern = r.pattern, caseSensitive = r.caseSensitive, enabled = r.enabled,
                categoryId = r.categoryId?.toString(), renameTo = r.renameTo, addTags = r.addTags,
                timesApplied = r.timesApplied, lastAppliedAt = r.lastAppliedAt?.let { df.format(it) }
            )
        }
        if (rows.isNotEmpty()) SupabaseService.client.postgrest["category_rules"].upsert(rows)
    }

    private suspend fun pullCategoryRules(db: AppDatabase, householdId: String) {
        val remote = SupabaseService.client.postgrest["category_rules"].select {
            filter {
                filter("household_id", FilterOperator.EQ, householdId)
                filter("deleted_at", FilterOperator.IS, "null")
            }
        }.decodeList<CategoryRuleRow>()
        for (row in remote) {
            val catId = row.categoryId?.let { UUID.fromString(it) }
            val lastApplied = row.lastAppliedAt?.let { try { df.parse(it) } catch (e: Exception) { null } }
            db.categoryRuleDao().insert(CategoryRuleEntity(
                id = UUID.fromString(row.id), priority = row.priority,
                matchField = row.matchField, matchKind = row.matchKind,
                pattern = row.pattern, caseSensitive = row.caseSensitive,
                enabled = row.enabled, categoryId = catId,
                renameTo = row.renameTo, addTags = row.addTags,
                timesApplied = row.timesApplied, lastAppliedAt = lastApplied
            ))
        }
    }

    // MARK: - Deletions (tombstones)

    private suspend fun pushDeletions(db: AppDatabase, householdId: String) {
        val tombstones = db.softDeleteTombstoneDao().getAll()
        if (tombstones.isEmpty()) return
        val now = df.format(Date())
        val payload = DeletedAtUpdate(deletedAt = now)
        for (tombstone in tombstones) {
            try {
                SupabaseService.client.postgrest[tombstone.table].update(payload) {
                    filter {
                        filter("id", FilterOperator.EQ, tombstone.recordID.toString())
                        filter("household_id", FilterOperator.EQ, householdId)
                    }
                }
                db.softDeleteTombstoneDao().delete(tombstone)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
