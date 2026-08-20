package com.truesummit.android.service

import android.content.Context
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.*
import com.truesummit.android.data.model.AccountType
import com.truesummit.android.data.model.GoalType
import com.truesummit.android.data.model.LiabilityKind
import com.truesummit.android.data.model.ScheduledKind
import com.truesummit.android.ui.budget.CategoryBarColor
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

class BudgetEngine(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(context.applicationContext)

    // MARK: - Pure calculations

    suspend fun availableToBudget(transactions: List<TransactionEntity>, budgetMonth: BudgetMonthEntity?, year: Int, month: Int): BigDecimal {
        val calendar = Calendar.getInstance()
        val inflow = transactions.filter {
            calendar.time = it.date
            it.amount > BigDecimal.ZERO &&
            calendar.get(Calendar.YEAR) == year &&
            (calendar.get(Calendar.MONTH) + 1) == month
        }.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }

        val allocations = budgetMonth?.let { db.budgetDao().getAllocationsForMonth(it.id).first() } ?: emptyList()
        val assigned = allocations.fold(BigDecimal.ZERO) { acc, alloc -> acc.add(alloc.amount) }
        val carry = budgetMonth?.carryover ?: BigDecimal.ZERO

        return inflow.add(carry).subtract(assigned)
    }

    suspend fun assigned(category: CategoryEntity, budgetMonth: BudgetMonthEntity?): BigDecimal {
        if (budgetMonth == null) return BigDecimal.ZERO
        return db.budgetDao().getAllocation(budgetMonth.id, category.id)?.amount ?: BigDecimal.ZERO
    }

    suspend fun activity(category: CategoryEntity, year: Int, month: Int): BigDecimal {
        val calendar = Calendar.getInstance()
        val transactions = db.transactionDao().getAll().first().filter { tx ->
            calendar.time = tx.date
            tx.categoryId == category.id &&
            calendar.get(Calendar.YEAR) == year &&
            (calendar.get(Calendar.MONTH) + 1) == month
        }
        // Also count split-transaction amounts assigned to this category.
        val allSplits = db.transactionDao().getAllSplits()
        val splitTotal = allSplits.filter { split ->
            if (split.categoryId != category.id) return@filter false
            val txId = split.transactionId ?: return@filter false
            val parent = transactions.find { it.id == txId }
                ?: db.transactionDao().getById(txId)
                ?: return@filter false
            calendar.time = parent.date
            calendar.get(Calendar.YEAR) == year && (calendar.get(Calendar.MONTH) + 1) == month
        }.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.amount) }

        // Direct transactions that are split should not double-count — subtract them and use splits.
        val splitParentIds = allSplits.map { it.transactionId }.toSet()
        val unsplitTotal = transactions.filter { it.id !in splitParentIds }
            .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }

        return unsplitTotal.add(splitTotal)
    }

    suspend fun available(category: CategoryEntity, budgetMonth: BudgetMonthEntity?, year: Int, month: Int): BigDecimal {
        return assigned(category, budgetMonth).add(activity(category, year, month))
    }

    // MARK: - Mutations

    suspend fun ensureMonth(year: Int, month: Int): BudgetMonthEntity {
        val existing = db.budgetDao().getMonth(year, month)
        if (existing != null) return existing
        val newMonth = BudgetMonthEntity(year = year, month = month, carryover = BigDecimal.ZERO)
        db.budgetDao().insertMonth(newMonth)
        if (BudgetRollover.isEnabled) seedRollover(newMonth)
        return newMonth
    }

    private suspend fun seedRollover(newMonth: BudgetMonthEntity) {
        val prevMonth = if (newMonth.month == 1) 12 else newMonth.month - 1
        val prevYear = if (newMonth.month == 1) newMonth.year - 1 else newMonth.year
        val prevMonthEntity = db.budgetDao().getMonth(prevYear, prevMonth) ?: return
        val transactions = db.transactionDao().getAll().first()
        val categories = db.categoryDao().getCategoriesList()
        for (category in categories) {
            if (BudgetRollover.isExcluded(category.id)) continue
            val avail = available(category, prevMonthEntity, prevYear, prevMonth)
            if (avail == BigDecimal.ZERO) continue
            val existing = db.budgetDao().getAllocation(newMonth.id, category.id)
            if (existing != null) {
                db.budgetDao().updateAllocation(existing.copy(amount = existing.amount.add(avail)))
            } else {
                db.budgetDao().insertAllocation(BudgetAllocationEntity(amount = avail, categoryId = category.id, monthId = newMonth.id))
            }
        }
    }

    suspend fun assign(amount: BigDecimal, category: CategoryEntity, budgetMonth: BudgetMonthEntity) {
        val existing = db.budgetDao().getAllocation(budgetMonth.id, category.id)
        if (existing != null) {
            db.budgetDao().updateAllocation(existing.copy(amount = existing.amount.add(amount)))
        } else {
            db.budgetDao().insertAllocation(BudgetAllocationEntity(amount = amount, categoryId = category.id, monthId = budgetMonth.id))
        }
    }

    suspend fun setAssigned(amount: BigDecimal, category: CategoryEntity, budgetMonth: BudgetMonthEntity) {
        val existing = db.budgetDao().getAllocation(budgetMonth.id, category.id)
        if (existing != null) {
            db.budgetDao().updateAllocation(existing.copy(amount = amount))
        } else {
            db.budgetDao().insertAllocation(BudgetAllocationEntity(amount = amount, categoryId = category.id, monthId = budgetMonth.id))
        }
    }

    // MARK: - Credit Card Reservation

    suspend fun applyCreditCardReservation(tx: TransactionEntity) {
        val accountId = tx.accountId ?: return
        val account = db.accountDao().getById(accountId) ?: return
        if (account.type != AccountType.CREDIT_CARD || tx.amount >= BigDecimal.ZERO) return
        val cal = Calendar.getInstance()
        cal.time = tx.date
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val bm = ensureMonth(year, month)
        val payment = paymentCategory(account) ?: return
        val splits = db.transactionDao().getSplitsForTransaction(tx.id)
        if (splits.isEmpty()) {
            val spending = tx.categoryId?.let { db.categoryDao().getCategoryById(it) } ?: return
            if (spending.id != payment.id) {
                transferAllocation(tx.amount.abs(), spending, payment, bm)
            }
        } else {
            for (split in splits) {
                val spending = split.categoryId?.let { db.categoryDao().getCategoryById(it) } ?: continue
                if (spending.id != payment.id) {
                    transferAllocation(split.amount.abs(), spending, payment, bm)
                }
            }
        }
    }

    suspend fun paymentCategory(account: AccountEntity): CategoryEntity? {
        return db.categoryDao().getCategoriesList().find { it.linkedAccountId == account.id }
    }

    suspend fun ensurePaymentCategory(account: AccountEntity): CategoryEntity? {
        paymentCategory(account)?.let { return it }
        val groups = db.categoryDao().getGroupsList()
        val ccGroup = groups.find { it.name == "Credit Card Payments" } ?: run {
            val nextSort = (groups.maxOfOrNull { it.sort } ?: -1) + 1
            val g = CategoryGroupEntity(name = "Credit Card Payments", sort = nextSort)
            db.categoryDao().insertGroup(g)
            g
        }
        val cat = CategoryEntity(name = account.name, sort = 0, groupId = ccGroup.id, linkedAccountId = account.id)
        db.categoryDao().insertCategory(cat)
        return cat
    }

    private suspend fun transferAllocation(amount: BigDecimal, source: CategoryEntity, target: CategoryEntity, bm: BudgetMonthEntity) {
        val sourceAlloc = db.budgetDao().getAllocation(bm.id, source.id)
        if (sourceAlloc != null) {
            db.budgetDao().updateAllocation(sourceAlloc.copy(amount = sourceAlloc.amount.subtract(amount)))
        } else {
            db.budgetDao().insertAllocation(BudgetAllocationEntity(amount = amount.negate(), categoryId = source.id, monthId = bm.id))
        }
        val targetAlloc = db.budgetDao().getAllocation(bm.id, target.id)
        if (targetAlloc != null) {
            db.budgetDao().updateAllocation(targetAlloc.copy(amount = targetAlloc.amount.add(amount)))
        } else {
            db.budgetDao().insertAllocation(BudgetAllocationEntity(amount = amount, categoryId = target.id, monthId = bm.id))
        }
    }

    // MARK: - Auto-assign

    suspend fun autoAssignAvailable(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        budgetMonth: BudgetMonthEntity
    ) {
        var remaining = availableToBudget(transactions, budgetMonth, budgetMonth.year, budgetMonth.month)
        if (remaining <= BigDecimal.ZERO) return

        val candidates = categories.filter { db.goalDao().getGoalForCategory(it.id) != null }

        for (cat in candidates) {
            if (remaining <= BigDecimal.ZERO) break
            val goal = db.goalDao().getGoalForCategory(cat.id) ?: continue
            val already = assigned(cat, budgetMonth)
            val avail = available(cat, budgetMonth, budgetMonth.year, budgetMonth.month)
            val needed = when (goal.type) {
                GoalType.MONTHLY_AMOUNT -> BigDecimal.ZERO.max(goal.targetAmount.subtract(already))
                GoalType.SAVINGS_TARGET, GoalType.BY_DATE_TARGET ->
                    BigDecimal.ZERO.max(goal.targetAmount.subtract(BigDecimal.ZERO.max(avail)))
            }
            val toAssign = remaining.min(needed)
            if (toAssign > BigDecimal.ZERO) {
                setAssigned(already.add(toAssign), cat, budgetMonth)
                remaining = remaining.subtract(toAssign)
            }
        }
    }

    suspend fun coverOverspending(
        source: CategoryEntity,
        target: CategoryEntity,
        amount: BigDecimal,
        budgetMonth: BudgetMonthEntity
    ) {
        val sourceAlloc = db.budgetDao().getAllocation(budgetMonth.id, source.id)
        val sourceAssigned = sourceAlloc?.amount ?: BigDecimal.ZERO
        val newSource = BigDecimal.ZERO.max(sourceAssigned.subtract(amount))
        val delta = sourceAssigned.subtract(newSource)
        if (sourceAlloc != null) {
            db.budgetDao().updateAllocation(sourceAlloc.copy(amount = newSource))
        }
        val targetAlloc = db.budgetDao().getAllocation(budgetMonth.id, target.id)
        if (targetAlloc != null) {
            db.budgetDao().updateAllocation(targetAlloc.copy(amount = targetAlloc.amount.add(delta)))
        } else {
            db.budgetDao().insertAllocation(BudgetAllocationEntity(amount = delta, categoryId = target.id, monthId = budgetMonth.id))
        }
    }

    // MARK: - Roll to next month

    /**
     * Projected monthly spend from MTD activity.
     * Only meaningful when viewing the current month and at least 5 days have elapsed.
     * Returns null otherwise.
     */
    fun projectedMonthlySpend(activity: BigDecimal, year: Int, month: Int): BigDecimal? {
        val now = Calendar.getInstance()
        if (now.get(Calendar.YEAR) != year || (now.get(Calendar.MONTH) + 1) != month) return null
        val spent = activity.negate()
        if (spent <= BigDecimal.ZERO) return null
        val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)
        if (dayOfMonth < 5) return null
        val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daily = spent.toDouble() / dayOfMonth
        return BigDecimal.valueOf(daily * daysInMonth)
    }

    /**
     * How much still needs to be assigned this month to stay on track for a goal.
     * Returns null for non-date-target goals. Returns 0 when already funded.
     */
    fun neededThisMonth(
        goal: GoalEntity,
        availableNow: BigDecimal,
        assignedThisMonth: BigDecimal,
        currentYear: Int,
        currentMonth: Int
    ): BigDecimal? {
        if (goal.type != GoalType.BY_DATE_TARGET) return null
        val targetDate = goal.targetDate ?: return null
        val cal = Calendar.getInstance()
        cal.time = targetDate
        val targetYear = cal.get(Calendar.YEAR)
        val targetMonth = cal.get(Calendar.MONTH) + 1
        val monthsLeft = maxOf(1, (targetYear - currentYear) * 12 + (targetMonth - currentMonth) + 1)
        val priorProgress = availableNow.subtract(assignedThisMonth).max(BigDecimal.ZERO)
        val stillNeeded = goal.targetAmount.subtract(priorProgress)
        if (stillNeeded <= BigDecimal.ZERO) return BigDecimal.ZERO
        val perMonth = stillNeeded.divide(BigDecimal(monthsLeft), 2, java.math.RoundingMode.UP)
        return perMonth.subtract(assignedThisMonth).max(BigDecimal.ZERO)
    }

    suspend fun rollToNextMonth(current: BudgetMonthEntity, transactions: List<TransactionEntity>, categories: List<CategoryEntity>) {
        val unassigned = availableToBudget(transactions, current, current.year, current.month)
        var overspentTotal = BigDecimal.ZERO
        for (cat in categories) {
            val avail = available(cat, current, current.year, current.month)
            if (avail < BigDecimal.ZERO) {
                overspentTotal = overspentTotal.add(avail)
            }
        }
        val carry = BigDecimal.ZERO.max(unassigned).add(overspentTotal)
        var nextYear = current.year
        var nextMonth = current.month + 1
        if (nextMonth > 12) { nextMonth = 1; nextYear++ }
        val nextMonthEntity = ensureMonth(nextYear, nextMonth)
        db.budgetDao().updateMonth(nextMonthEntity.copy(carryover = carry))
    }

    // MARK: - Scheduled items

    suspend fun postOne(item: ScheduledItemEntity) {
        val tx = TransactionEntity(
            date = item.nextDate,
            amount = item.amount,
            merchant = item.name,
            memo = null,
            cleared = false,
            flagColor = null,
            pfcPrimary = null,
            accountId = item.accountId,
            categoryId = item.categoryId
        )
        db.transactionDao().insert(tx)
        if (item.intervalDays > 0) {
            val cal = Calendar.getInstance()
            cal.time = item.nextDate
            cal.add(Calendar.DAY_OF_YEAR, item.intervalDays)
            db.scheduledItemDao().update(item.copy(nextDate = cal.time))
        }
    }

    suspend fun postAllDue() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val items = db.scheduledItemDao().getAll().first()
        for (item in items) {
            var safety = 0
            while (item.nextDate.before(today) && safety < 365) {
                postOne(item)
                safety++
            }
        }
    }

    // MARK: - Category merge

    suspend fun merge(source: CategoryEntity, into: CategoryEntity) {
        if (source.id == into.id) return
        val transactions = db.transactionDao().getAll().first().filter { it.categoryId == source.id }
        for (tx in transactions) {
            db.transactionDao().update(tx.copy(categoryId = into.id))
        }
        val splits = db.transactionDao().getAllSplits().filter { it.categoryId == source.id }
        for (split in splits) {
            db.transactionDao().updateSplit(split.copy(categoryId = into.id))
        }
        val sourceAllocs = db.budgetDao().getAllocationsForCategory(source.id)
        for (alloc in sourceAllocs) {
            val monthId = alloc.monthId ?: continue
            val existing = db.budgetDao().getAllocation(monthId, into.id)
            if (existing != null) {
                db.budgetDao().updateAllocation(existing.copy(amount = existing.amount.add(alloc.amount)))
            } else {
                db.budgetDao().insertAllocation(alloc.copy(id = UUID.randomUUID(), categoryId = into.id))
            }
            db.budgetDao().deleteAllocation(alloc)
        }
        db.categoryDao().deleteCategory(source)
    }

    // MARK: - CSV Import

    data class ImportResult(val imported: Int, val skipped: Int, val errors: List<String>)

    suspend fun importCSV(
        content: String,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): ImportResult {
        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split("\n").filter { it.isNotBlank() }
        if (lines.size <= 1) {
            errors.add("No data rows found.")
            return ImportResult(imported, skipped, errors)
        }
        val header = parseCSVLine(lines[0]).map { it.lowercase() }
        val dateIdx = header.indexOf("date").takeIf { it >= 0 }
        val merchantIdx = header.indexOf("merchant").takeIf { it >= 0 }
        val amountIdx = header.indexOf("amount").takeIf { it >= 0 }
        if (dateIdx == null || merchantIdx == null || amountIdx == null) {
            errors.add("Header must include: date, merchant, amount (also optional: account, category, memo).")
            return ImportResult(imported, skipped, errors)
        }
        val accountIdx = header.indexOf("account").takeIf { it >= 0 }
        val categoryIdx = header.indexOf("category").takeIf { it >= 0 }
        val memoIdx = header.indexOf("memo").takeIf { it >= 0 }

        val formatters = listOf("yyyy-MM-dd", "MM/dd/yyyy", "yyyy/MM/dd", "dd/MM/yyyy").map { fmt ->
            // Non-lenient, or "MM/dd/yyyy" happily reads 13/01/2026 as a date in
            // the following year and silently files the row under the wrong month.
            SimpleDateFormat(fmt, Locale.US).apply { isLenient = false }
        }

        // SimpleDateFormat.parse throws rather than returning null, so a plain
        // firstNotNullOfOrNull lets the first mismatched format abort the whole
        // import — which is every Mint export, since those are MM/dd/yyyy and
        // the ISO pattern is tried first.
        fun parseDate(s: String): Date? {
            val trimmed = s.trim()
            if (trimmed.isEmpty()) return null
            return formatters.firstNotNullOfOrNull { runCatching { it.parse(trimmed) }.getOrNull() }
        }

        for (line in lines.drop(1)) {
            val fields = parseCSVLine(line)
            val maxIdx = maxOf(dateIdx, merchantIdx, amountIdx)
            if (fields.size <= maxIdx) { skipped++; continue }
            val dateStr = fields[dateIdx]
            val merchant = fields[merchantIdx]
            val amountStr = fields[amountIdx].replace("$", "").replace(",", "")
            val date = parseDate(dateStr)
            val amount = amountStr.toBigDecimalOrNull()
            if (date == null || amount == null) { skipped++; errors.add("Skipped: $line"); continue }
            fun at(idx: Int?) = if (idx != null && idx < fields.size) fields[idx] else ""
            val accountName = at(accountIdx)
            val categoryName = at(categoryIdx)
            val memo = at(memoIdx)
            val account = accounts.find { it.name.equals(accountName, ignoreCase = true) }
            val category = categories.find { it.name.equals(categoryName, ignoreCase = true) }
            val tx = TransactionEntity(
                date = date, amount = amount, merchant = merchant,
                memo = memo.ifBlank { null }, cleared = false,
                flagColor = null, pfcPrimary = null,
                accountId = account?.id, categoryId = category?.id
            )
            db.transactionDao().insert(tx)
            imported++
        }
        return ImportResult(imported, skipped, errors)
    }

    private fun parseCSVLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString().trim()); current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString().trim())
        return fields
    }

    // MARK: - Quick assign helpers

    suspend fun lastMonthAssigned(category: CategoryEntity, currentYear: Int, currentMonth: Int): BigDecimal {
        var prevY = currentYear
        var prevM = currentMonth - 1
        if (prevM < 1) { prevM = 12; prevY-- }
        val prev = db.budgetDao().getMonth(prevY, prevM) ?: return BigDecimal.ZERO
        return db.budgetDao().getAllocation(prev.id, category.id)?.amount ?: BigDecimal.ZERO
    }

    suspend fun averageAssigned(category: CategoryEntity, monthsBack: Int, currentYear: Int, currentMonth: Int): BigDecimal {
        var total = BigDecimal.ZERO
        var count = 0
        var y = currentYear
        var m = currentMonth - 1
        repeat(monthsBack) {
            if (m < 1) { m = 12; y-- }
            db.budgetDao().getMonth(y, m)?.let { bm ->
                total = total.add(db.budgetDao().getAllocation(bm.id, category.id)?.amount ?: BigDecimal.ZERO)
                count++
            }
            m--
        }
        return if (count > 0) total.divide(BigDecimal(count), 2, java.math.RoundingMode.HALF_UP) else BigDecimal.ZERO
    }

    // MARK: - Reset & Seed

    /**
     * Deletes every local record.
     *
     * [reseed] restores the sample accounts and transactions afterwards. It
     * defaults to false: the main reason to call this is to get *rid* of the
     * sample data, and re-creating it was the opposite of what that needs.
     */
    suspend fun resetAllData(reseed: Boolean = false) {
        db.transactionDao().getAll().first().forEach { tx ->
            db.transactionDao().deleteSplitsForTransaction(tx.id)
            db.transactionDao().delete(tx)
        }
        db.budgetDao().getAllAllocations().forEach { db.budgetDao().deleteAllocation(it) }
        db.budgetDao().getAllMonths().forEach { db.budgetDao().updateMonth(it.copy(carryover = BigDecimal.ZERO)) }
        db.goalDao().getAllGoals().first().forEach { db.goalDao().delete(it) }
        db.scheduledItemDao().getAll().first().forEach { db.scheduledItemDao().delete(it) }
        db.categoryDao().getCategoriesList().forEach { db.categoryDao().deleteCategory(it) }
        db.accountDao().getAll().first().forEach { db.accountDao().delete(it) }
        if (reseed) seedIfNeeded()
    }

    /**
     * Everything the month-by-month seeding needs to refer back to.
     *
     * The seed used to be one function holding all of this as locals. It grew
     * past the JVM's 64KB per-method limit, so the stages are separate methods
     * now and pass this between them.
     */
    private data class SeedRefs(
        val checking: AccountEntity,
        val savings: AccountEntity,
        val creditCard: AccountEntity,
        val brokerage: AccountEntity,
        val retirement: AccountEntity,
        val studentLoan: AccountEntity,
        val housing: CategoryEntity,
        val utilities: CategoryEntity,
        val groceries: CategoryEntity,
        val transport: CategoryEntity,
        val insurance: CategoryEntity,
        val dining: CategoryEntity,
        val subscriptions: CategoryEntity,
        val personalCare: CategoryEntity,
        val travel: CategoryEntity,
        val gifts: CategoryEntity,
        val debtRepay: CategoryEntity,
        val savingsInv: CategoryEntity,
        val japanTrip: CategoryEntity,
        val downPayment: CategoryEntity,
        val creditCardCat: CategoryEntity
    )

    suspend fun seedIfNeeded() {
        val existing = db.accountDao().getAll().first()
        if (existing.isNotEmpty()) return

        val refs = seedAccountsAndCategories()
        seedGoalsAndSchedule(refs)
        seedMonthlyHistory(refs)
        seedInvestments(refs.brokerage, refs.retirement)
        seedLiabilities(refs.creditCard, refs.studentLoan)
    }

    private suspend fun seedAccountsAndCategories(): SeedRefs {
        // ── Accounts ────────────────────────────────────────────────────────
        val checking    = AccountEntity(name = "Chase Checking",  type = AccountType.CHECKING,    balance = BigDecimal("4820"),   currencyCode = "USD")
        val savings     = AccountEntity(name = "High-Yield Savings", type = AccountType.SAVINGS,  balance = BigDecimal("12400"),  currencyCode = "USD")
        val creditCard  = AccountEntity(name = "Visa Signature",  type = AccountType.CREDIT_CARD, balance = BigDecimal("-680"),   currencyCode = "USD")
        val brokerage   = AccountEntity(name = "Brokerage",       type = AccountType.INVESTMENT,  balance = BigDecimal("18500"),  currencyCode = "USD")
        val retirement  = AccountEntity(name = "401(k)",          type = AccountType.RETIREMENT,  balance = BigDecimal("54200"),  currencyCode = "USD")
        val studentLoan = AccountEntity(name = "Student Loan",    type = AccountType.LOAN,        balance = BigDecimal("-18300"), currencyCode = "USD")
        for (it in listOf(checking, savings, creditCard, brokerage, retirement, studentLoan)) {
            db.accountDao().insert(it)
        }

        // ── Category groups & categories ────────────────────────────────────
        val needs       = CategoryGroupEntity(name = "Needs (Fixed Expenses)",    sort = 0)
        val wants       = CategoryGroupEntity(name = "Wants (Flexible Expenses)", sort = 1)
        val savingsGrp  = CategoryGroupEntity(name = "Savings & Debt",            sort = 2)
        val goalsGrp    = CategoryGroupEntity(name = "Goals",                     sort = 3)
        val cardPayGrp  = CategoryGroupEntity(name = "Credit Card Payments",      sort = 4)
        for (it in listOf(needs, wants, savingsGrp, goalsGrp, cardPayGrp)) {
            db.categoryDao().insertGroup(it)
        }

        val housing     = CategoryEntity(name = "Housing",                    sort = 0, groupId = needs.id,      linkedAccountId = null)
        val utilities   = CategoryEntity(name = "Utilities",                  sort = 1, groupId = needs.id,      linkedAccountId = null)
        val groceries   = CategoryEntity(name = "Groceries",                  sort = 2, groupId = needs.id,      linkedAccountId = null)
        val transport   = CategoryEntity(name = "Transportation",              sort = 3, groupId = needs.id,      linkedAccountId = null)
        val insurance   = CategoryEntity(name = "Insurance",                  sort = 4, groupId = needs.id,      linkedAccountId = null)
        val dining      = CategoryEntity(name = "Dining Out & Entertainment", sort = 0, groupId = wants.id,      linkedAccountId = null)
        val subscriptions = CategoryEntity(name = "Subscriptions",            sort = 1, groupId = wants.id,      linkedAccountId = null)
        val personalCare  = CategoryEntity(name = "Personal Care & Clothing", sort = 2, groupId = wants.id,      linkedAccountId = null)
        val travel      = CategoryEntity(name = "Vacation & Travel",          sort = 3, groupId = wants.id,      linkedAccountId = null)
        val gifts       = CategoryEntity(name = "Gifts & Donations",          sort = 4, groupId = wants.id,      linkedAccountId = null)
        val debtRepay   = CategoryEntity(name = "Debt Repayment",             sort = 0, groupId = savingsGrp.id, linkedAccountId = null)
        val savingsInv  = CategoryEntity(name = "Savings & Investments",      sort = 1, groupId = savingsGrp.id, linkedAccountId = null)
        val japanTrip   = CategoryEntity(name = "Japan Trip",                 sort = 0, groupId = goalsGrp.id,   linkedAccountId = null)
        val downPayment = CategoryEntity(name = "House Down Payment",         sort = 1, groupId = goalsGrp.id,   linkedAccountId = null)
        val creditCardCat = CategoryEntity(name = creditCard.name,            sort = 0, groupId = cardPayGrp.id, linkedAccountId = creditCard.id)
        for (it in listOf(housing, utilities, groceries, transport, insurance, dining, subscriptions,
            personalCare, travel, gifts, debtRepay, savingsInv, japanTrip, downPayment, creditCardCat)) {
            db.categoryDao().insertCategory(it)
        }

        // Pin a bar colour per category. Without one, the colour comes from a
        // hash of the category name over a four-colour cycle that includes
        // rose — so roughly a quarter of the categories drew a red bar with no
        // overspend behind it, which reads as a warning it isn't.
        val barColors = listOf(
            housing       to 0xFF0A84FF, // blue
            utilities     to 0xFF64D2FF, // cyan
            groceries     to 0xFF34C759, // green
            transport     to 0xFF5E5CE6, // indigo
            insurance     to 0xFF9B8EC4, // lavender
            dining        to 0xFF4ECDC4, // teal
            subscriptions to 0xFFBF5AF2, // purple
            personalCare  to 0xFF66D4CF, // mint
            travel        to 0xFF64D2FF, // cyan
            gifts         to 0xFF9B8EC4, // lavender
            debtRepay     to 0xFF4ECDC4, // teal
            savingsInv    to 0xFF34C759, // green
            japanTrip     to 0xFF0A84FF, // blue
            downPayment   to 0xFF5E5CE6, // indigo
            creditCardCat to 0xFF9B8EC4  // lavender
        )
        for ((category, argb) in barColors) {
            CategoryBarColor.setColor(appContext, category.id, androidx.compose.ui.graphics.Color(argb))
        }

        return SeedRefs(
            checking, savings, creditCard, brokerage, retirement, studentLoan,
            housing, utilities, groceries, transport, insurance, dining, subscriptions,
            personalCare, travel, gifts, debtRepay, savingsInv, japanTrip, downPayment, creditCardCat
        )
    }

    private suspend fun seedGoalsAndSchedule(r: SeedRefs) {
        // ── Goals ────────────────────────────────────────────────────────────
        // Relative to today so the sample never ages into the past.
        val targetJapan = monthsFromNow(14)
        val targetDown  = monthsFromNow(34)
        db.goalDao().insert(GoalEntity(type = GoalType.BY_DATE_TARGET,  targetAmount = BigDecimal("5000"),  targetDate = targetJapan, categoryId = r.japanTrip.id))
        db.goalDao().insert(GoalEntity(type = GoalType.BY_DATE_TARGET,  targetAmount = BigDecimal("60000"), targetDate = targetDown,  categoryId = r.downPayment.id))
        db.goalDao().insert(GoalEntity(type = GoalType.SAVINGS_TARGET,  targetAmount = BigDecimal("25000"), targetDate = null,        categoryId = r.savingsInv.id))

        // ── Scheduled bills ──────────────────────────────────────────────────
        // Outflows are stored negative and paychecks positive — the forecaster
        // just sums item.amount, and the add-bill dialog negates what the user
        // types. Seeding bills as positive made every one of them *raise* the
        // projected balance.
        val on1st  = nextOccurrenceOfDay(1)
        val on5th  = nextOccurrenceOfDay(5)
        val on10th = nextOccurrenceOfDay(10)
        val on15th = nextOccurrenceOfDay(15)

        suspend fun bill(name: String, amount: String, on: Date, categoryId: java.util.UUID?, kind: ScheduledKind = ScheduledKind.BILL) =
            db.scheduledItemDao().insert(
                ScheduledItemEntity(
                    kind = kind, name = name, amount = BigDecimal(amount).negate(),
                    nextDate = on, intervalDays = 30, accountId = r.checking.id, categoryId = categoryId
                )
            )

        bill("Rent",          "1850", on1st,  r.housing.id)
        bill("Electric",      "95",   on15th, r.utilities.id)
        bill("Internet",      "65",   on1st,  r.utilities.id)
        bill("Spotify",       "11",   on1st,  r.subscriptions.id, ScheduledKind.SUBSCRIPTION)
        bill("Netflix",       "15",   on1st,  r.subscriptions.id, ScheduledKind.SUBSCRIPTION)
        bill("Car Insurance", "142",  on1st,  r.insurance.id)

        // Recurring costs the old seed left out of the forecast entirely. With
        // only six bills scheduled against a full salary, the projection
        // implied saving over $4,000 a month.
        bill("Student Loan",  "300",  on10th, r.debtRepay.id)
        bill("Groceries",     "430",  on5th,  r.groceries.id)
        bill("Gas",           "100",  on5th,  r.transport.id)
        bill("Dining Out",    "300",  on10th, r.dining.id)
        bill("Personal Care", "120",  on15th, r.personalCare.id)

        db.scheduledItemDao().insert(
            ScheduledItemEntity(
                kind = ScheduledKind.PAYCHECK, name = "Paycheck", amount = BigDecimal("3200"),
                nextDate = on1st, intervalDays = 14, accountId = r.checking.id, categoryId = null
            )
        )
    }

    private suspend fun seedMonthlyHistory(r: SeedRefs) {
        val cal = Calendar.getInstance()
        val curYear  = cal.get(Calendar.YEAR)
        val curMonth = cal.get(Calendar.MONTH) + 1

        val months = (5 downTo 0).map { offset ->
            val c = Calendar.getInstance()
            c.add(Calendar.MONTH, -offset)
            c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
        }

        // Income varies slightly each month (bi-weekly pay = 2 or 3 cheques)
        val incomes = listOf(7200, 7200, 10800, 7200, 7200, 7200)

        for ((idx, ms) in months.withIndex()) {
            val (year, month) = ms
            val isCurrent = (year == curYear && month == curMonth)

            // Reuse the month row if one already exists. Merely opening the
            // Budget tab calls ensureMonth() for the current month, so a blind
            // insert here left two rows for it — and getMonth() returns the
            // empty one, so the current month rendered with nothing assigned.
            val bm = db.budgetDao().getMonth(year, month)
                ?: BudgetMonthEntity(year = year, month = month, carryover = BigDecimal.ZERO)
                    .also { db.budgetDao().insertMonth(it) }

            seedAllocations(r, bm, idx)
            seedTransactions(r, year, month, idx, isCurrent, BigDecimal(incomes[idx]))
            seedSnapshots(r, year, month, idx, isCurrent)
        }
    }

    private suspend fun seedAllocations(r: SeedRefs, bm: BudgetMonthEntity, idx: Int) {
        suspend fun alloc(amount: Int, catId: java.util.UUID) =
            db.budgetDao().insertAllocation(BudgetAllocationEntity(amount = BigDecimal(amount), categoryId = catId, monthId = bm.id))

        // Each allocation sits comfortably above what the month actually
        // spends. Budgets funded to the exact dollar left every category
        // bar pinned at 100% — technically on budget, but rendered in the
        // same red as an overspend.
        alloc(1850, r.housing.id)
        alloc(210,  r.utilities.id)
        alloc(520,  r.groceries.id)
        alloc(260,  r.transport.id)
        alloc(142,  r.insurance.id)
        alloc(360,  r.dining.id)
        alloc(90,   r.subscriptions.id)
        alloc(170,  r.personalCare.id)
        alloc(if (idx == 2) 1400 else 130, r.travel.id)   // big travel month (month 3)
        alloc(90,   r.gifts.id)
        alloc(300,  r.debtRepay.id)
        alloc(500,  r.savingsInv.id)
        alloc(400,  r.japanTrip.id)
        alloc(300,  r.downPayment.id)
        alloc(680,  r.creditCardCat.id)
    }

    private suspend fun seedTransactions(
        r: SeedRefs,
        year: Int,
        month: Int,
        idx: Int,
        isCurrent: Boolean,
        income: BigDecimal
    ) {
        fun tx(day: Int, amount: String, merchant: String, catId: java.util.UUID?, acctId: java.util.UUID = r.checking.id, pfcPrimary: String? = null) =
            TransactionEntity(date = date(year, month, day), amount = BigDecimal(amount), merchant = merchant,
                memo = null, cleared = true, flagColor = null, pfcPrimary = pfcPrimary, accountId = acctId, categoryId = catId)

        // The current month is only partway through, so it gets a partial
        // set of spending. Seeding a whole month of outflows into it made
        // every category read as fully spent, and the pace projection
        // (spend-to-date scaled to month length) then flagged them all.
        val lastSpendDay = if (isCurrent) 10 else 31

        suspend fun post(day: Int, amount: String, merchant: String, catId: java.util.UUID?, acctId: java.util.UUID = r.checking.id, pfcPrimary: String? = null) {
            if (day > lastSpendDay) return
            db.transactionDao().insert(tx(day, amount, merchant, catId, acctId, pfcPrimary))
        }

        // Income is never withheld — clipping a paycheck would leave the
        // month short of what its categories are already assigned.
        db.transactionDao().insert(tx(1,  income.divide(BigDecimal(2)).toPlainString(), "Acme Corp Payroll", null, r.checking.id, "Income"))
        db.transactionDao().insert(tx(15, income.divide(BigDecimal(2)).toPlainString(), "Acme Corp Payroll", null, r.checking.id, "Income"))
        if (idx == 2) db.transactionDao().insert(tx(28, "3600", "Acme Corp Bonus",  null, r.checking.id, "Income"))

        // Fixed bills that consume their whole category in one payment.
        // In the current month they're still upcoming, which keeps those
        // categories showing as funded rather than spent to the dollar.
        if (!isCurrent) {
            db.transactionDao().insert(tx(1,  "-1850", "Elm Street Apartments", r.housing.id))
            db.transactionDao().insert(tx(1,  "-142",  "GEICO",                 r.insurance.id))
            db.transactionDao().insert(tx(10, "-300",  "Navient Student Loan",  r.debtRepay.id))
            db.transactionDao().insert(tx(20, "-680",  "Visa Signature Payment", r.creditCardCat.id))
            // Tagged as a transfer so the review inbox doesn't ask for a
            // category on the receiving side of a payment already
            // categorised on the checking side.
            db.transactionDao().insert(tx(20, "680",   "Credit Card Payment",   null, r.creditCard.id, "TRANSFER_IN"))
        }

        // Utilities (vary a little)
        val elec = listOf(88, 95, 102, 91, 97, 89)
        post(12, "-${elec[idx]}", "City Power Co", r.utilities.id)
        post(3,  "-65",  "Xfinity Internet",   r.utilities.id)
        post(10, "-14",  "Water Utility",      r.utilities.id)

        // Groceries (3–4 trips)
        val grocTotal = listOf(390, 418, 445, 402, 411, 428)
        post(3,  "-${(grocTotal[idx] * 0.30).toInt()}", "Whole Foods",  r.groceries.id)
        post(9,  "-${(grocTotal[idx] * 0.25).toInt()}", "Trader Joe's", r.groceries.id)
        post(17, "-${(grocTotal[idx] * 0.26).toInt()}", "Kroger",       r.groceries.id)
        post(24, "-${(grocTotal[idx] * 0.19).toInt()}", "Costco",       r.groceries.id)

        // Transportation
        post(5,  "-52",  "Shell Gas",          r.transport.id)
        post(18, "-48",  "Circle K",           r.transport.id)
        if (idx % 2 == 0) post(22, "-35", "Lyft", r.transport.id)

        // Dining
        val diningMerchants = listOf(
            listOf("-28" to "Chipotle", "-62" to "Nobu", "-18" to "Starbucks", "-24" to "Shake Shack"),
            listOf("-35" to "Sushi Nakazawa", "-22" to "Dunkin", "-31" to "The Spotted Pig", "-19" to "Sweetgreen"),
            listOf("-44" to "Eleven Madison Park", "-18" to "Blue Bottle Coffee", "-29" to "Joe's Pizza", "-38" to "Momofuku"),
            listOf("-26" to "Tacos El Pastor", "-15" to "Starbucks", "-42" to "Gramercy Tavern", "-27" to "Dig"),
            listOf("-33" to "Emily Restaurant", "-21" to "Think Coffee", "-25" to "Shake Shack", "-30" to "Cote"),
            listOf("-29" to "Los Tacos No.1", "-17" to "La Colombe", "-48" to "Le Bernardin", "-22" to "Sweetgreen")
        )
        diningMerchants[idx].forEachIndexed { i, (amt, name) ->
            post(6 + i * 5, amt, name, r.dining.id, r.creditCard.id)
        }

        // Subscriptions
        post(1,  "-11",  "Spotify",       r.subscriptions.id)
        post(1,  "-15",  "Netflix",       r.subscriptions.id)
        post(5,  "-10",  "Apple iCloud+", r.subscriptions.id)
        if (idx == 0 || idx == 3) post(8, "-5", "NYT Digital", r.subscriptions.id)

        // Personal care / clothing
        val pcMerchants = listOf(
            listOf("-65" to "Warby Parker", "-38" to "CVS Pharmacy"),
            listOf("-22" to "Target Beauty", "-55" to "Uniqlo"),
            listOf("-80" to "Nordstrom Rack", "-19" to "Walgreens"),
            listOf("-45" to "Aesop", "-28" to "Target"),
            listOf("-110" to "Allbirds", "-15" to "CVS Pharmacy"),
            listOf("-35" to "Glossier", "-42" to "H&M")
        )
        pcMerchants[idx].forEach { (amt, name) ->
            post(14, amt, name, r.personalCare.id, r.creditCard.id)
        }

        // Travel (month 2 = big trip)
        if (idx == 2) {
            post(8,  "-620",  "Delta Airlines",   r.travel.id)
            post(9,  "-380",  "Marriott Miami",   r.travel.id)
            post(10, "-95",   "Hertz Car Rental", r.travel.id)
            post(11, "-68",   "Miami Beach Rest.", r.travel.id, r.creditCard.id)
        } else {
            // Months with no trip get no travel row at all — the old list
            // carried zeros, which posted $0.00 transactions.
            val fare = listOf(0, 45, 0, 38, 0, 55)[idx]
            if (fare > 0) post(20, "-$fare", "Uber", r.travel.id)
        }

        // Gifts / donations
        if (idx % 3 == 0) post(25, "-50", "Charity: Water", r.gifts.id)
        if (idx == 1)     post(20, "-80", "Amazon (gift)",  r.gifts.id)

        // Savings transfer
        post(2,  "-500", "Transfer to Savings", r.savingsInv.id)
        post(2,  "500",  "Transfer from Checking", r.savingsInv.id, r.savings.id, "TRANSFER_IN")

        // Goal contributions
        post(3, "-400", "Japan Trip Fund",   r.japanTrip.id)
        post(3, "-300", "Down Payment Fund", r.downPayment.id)
    }

    private suspend fun seedSnapshots(r: SeedRefs, year: Int, month: Int, idx: Int, isCurrent: Boolean) {
        // The current month is snapshotted as of today rather than the 28th, so
        // the net-worth chart runs all the way to the right edge instead of
        // stopping a month short.
        val checkingBal   = BigDecimal(listOf(3100, 3350, 5200, 3600, 3800, 4820)[idx])
        val savingsBal    = BigDecimal(listOf(9800, 10300, 10800, 11300, 11850, 12400)[idx])
        val brokerageBal  = BigDecimal(listOf(14200, 15100, 16000, 16800, 17600, 18500)[idx])
        val retirementBal = BigDecimal(listOf(48000, 49200, 50400, 51400, 52800, 54200)[idx])
        val loanBal       = BigDecimal(listOf(-19800, -19500, -19200, -18900, -18600, -18300)[idx])
        val cardBal       = BigDecimal(listOf(-720, -655, -910, -600, -745, -680)[idx])
        val snapDate = if (isCurrent) Date() else date(year, month, 28)
        db.netWorthDao().insertSnapshot(BalanceSnapshotEntity(date = snapDate, balance = checkingBal,   accountId = r.checking.id))
        db.netWorthDao().insertSnapshot(BalanceSnapshotEntity(date = snapDate, balance = savingsBal,    accountId = r.savings.id))
        db.netWorthDao().insertSnapshot(BalanceSnapshotEntity(date = snapDate, balance = brokerageBal,  accountId = r.brokerage.id))
        db.netWorthDao().insertSnapshot(BalanceSnapshotEntity(date = snapDate, balance = retirementBal, accountId = r.retirement.id))
        db.netWorthDao().insertSnapshot(BalanceSnapshotEntity(date = snapDate, balance = loanBal,       accountId = r.studentLoan.id))
        db.netWorthDao().insertSnapshot(BalanceSnapshotEntity(date = snapDate, balance = cardBal,       accountId = r.creditCard.id))
    }

    /**
     * Holdings and trades for the brokerage and 401(k), so the investment
     * surfaces have something to show instead of an empty state.
     */
    private suspend fun seedInvestments(brokerage: AccountEntity, retirement: AccountEntity) {
        val asOf = Date()

        data class Holding(
            val ticker: String,
            val name: String,
            val type: String,
            val quantity: String,
            val price: String,
            val costBasis: String
        )

        val brokerageHoldings = listOf(
            Holding("VTI",  "Vanguard Total Stock Market ETF", "etf",           "42.5",  "268.40", "9_820"),
            Holding("VXUS", "Vanguard Total International ETF", "etf",          "58.0",  "64.15",  "3_410"),
            Holding("AAPL", "Apple Inc.",                      "equity",        "18.0",  "214.80", "2_960"),
            Holding("MSFT", "Microsoft Corporation",           "equity",        "9.0",   "426.10", "3_180"),
            Holding("BND",  "Vanguard Total Bond Market ETF",  "etf",           "22.0",  "73.55",  "1_680")
        )
        val retirementHoldings = listOf(
            Holding("VFIAX", "Vanguard 500 Index Admiral",     "mutual fund",   "62.0",  "528.90", "27_400"),
            Holding("VTIAX", "Vanguard Total Intl Index Admiral", "mutual fund","310.0", "36.40",  "9_900"),
            Holding("VBTLX", "Vanguard Total Bond Index Admiral", "mutual fund","480.0", "9.68",   "4_780")
        )

        suspend fun insertHoldings(account: AccountEntity, holdings: List<Holding>, prefix: String) {
            for ((i, h) in holdings.withIndex()) {
                val qty = BigDecimal(h.quantity)
                val price = BigDecimal(h.price)
                db.investmentDao().insertHolding(
                    InvestmentHoldingEntity(
                        plaidHoldingKey = "$prefix-holding-$i",
                        plaidAccountId = "$prefix-account",
                        plaidSecurityId = "$prefix-security-$i",
                        tickerSymbol = h.ticker,
                        securityName = h.name,
                        securityType = h.type,
                        isCashEquivalent = false,
                        quantity = qty,
                        institutionPrice = price,
                        institutionValue = qty.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP),
                        costBasis = BigDecimal(h.costBasis.replace("_", "")),
                        currencyCode = "USD",
                        asOfDate = asOf,
                        accountId = account.id
                    )
                )
            }
        }

        insertHoldings(brokerage, brokerageHoldings, "brokerage")
        insertHoldings(retirement, retirementHoldings, "retirement")

        // Cash sweep so the allocation breakdown has a cash slice.
        db.investmentDao().insertHolding(
            InvestmentHoldingEntity(
                plaidHoldingKey = "brokerage-cash",
                plaidAccountId = "brokerage-account",
                plaidSecurityId = "brokerage-cash-security",
                tickerSymbol = "CUR:USD",
                securityName = "Cash Sweep",
                securityType = "cash",
                isCashEquivalent = true,
                quantity = BigDecimal("640"),
                institutionPrice = BigDecimal("1"),
                institutionValue = BigDecimal("640"),
                costBasis = BigDecimal("640"),
                currencyCode = "USD",
                asOfDate = asOf,
                accountId = brokerage.id
            )
        )

        // Six months of contributions, one buy per month per account, plus a
        // couple of dividends so the activity list is not all one type.
        var seq = 0
        for (offset in 5 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.MONTH, -offset)
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH) + 1

            suspend fun invTx(
                account: AccountEntity,
                day: Int,
                name: String,
                amount: String,
                type: String,
                subtype: String,
                ticker: String?,
                securityName: String?,
                quantity: String?,
                price: String?
            ) {
                db.investmentDao().insertTransaction(
                    InvestmentTransactionEntity(
                        plaidInvestmentTransactionId = "inv-tx-${seq++}",
                        date = date(year, month, day),
                        name = name,
                        amount = BigDecimal(amount),
                        fees = BigDecimal.ZERO,
                        quantity = quantity?.let { BigDecimal(it) },
                        price = price?.let { BigDecimal(it) },
                        type = type,
                        subtype = subtype,
                        plaidSecurityId = null,
                        tickerSymbol = ticker,
                        securityName = securityName,
                        currencyCode = "USD",
                        accountId = account.id
                    )
                )
            }

            invTx(brokerage, 5, "Buy VTI", "500", "buy", "buy", "VTI", "Vanguard Total Stock Market ETF", "1.86", "268.40")
            invTx(retirement, 15, "401(k) Contribution — VFIAX", "950", "buy", "buy", "VFIAX", "Vanguard 500 Index Admiral", "1.79", "528.90")
            if (offset % 3 == 0) {
                invTx(brokerage, 22, "VTI Dividend", "-58", "cash", "dividend", "VTI", "Vanguard Total Stock Market ETF", null, null)
                invTx(brokerage, 22, "AAPL Dividend", "-12", "cash", "dividend", "AAPL", "Apple Inc.", null, null)
            }
        }
    }

    /**
     * Liability detail for the card and the student loan — APRs, minimums, and
     * due dates are what the debt payoff and bill surfaces read.
     */
    private suspend fun seedLiabilities(creditCard: AccountEntity, studentLoan: AccountEntity) {
        val now = Date()
        db.liabilityDao().insert(
            LiabilityEntity(
                plaidAccountId = "sample-credit-card",
                kind = LiabilityKind.CREDIT,
                lastStatementBalance = BigDecimal("680"),
                lastStatementIssueDate = daysFromNow(-12),
                minimumPayment = BigDecimal("35"),
                nextPaymentDueDate = daysFromNow(9),
                lastPaymentAmount = BigDecimal("745"),
                lastPaymentDate = daysFromNow(-30),
                interestRatePercentage = BigDecimal("19.99"),
                originationPrincipal = null,
                originationDate = null,
                maturityDate = null,
                loanName = null,
                rawJSON = null,
                updatedAt = now,
                accountId = creditCard.id
            )
        )
        db.liabilityDao().insert(
            LiabilityEntity(
                plaidAccountId = "sample-student-loan",
                kind = LiabilityKind.STUDENT,
                lastStatementBalance = BigDecimal("18300"),
                lastStatementIssueDate = daysFromNow(-18),
                minimumPayment = BigDecimal("300"),
                nextPaymentDueDate = daysFromNow(16),
                lastPaymentAmount = BigDecimal("300"),
                lastPaymentDate = daysFromNow(-14),
                interestRatePercentage = BigDecimal("5.25"),
                originationPrincipal = BigDecimal("32000"),
                originationDate = monthsFromNow(-96),
                maturityDate = monthsFromNow(84),
                loanName = "Navient Student Loan",
                rawJSON = null,
                updatedAt = now,
                accountId = studentLoan.id
            )
        )
    }

    /** Midday on the same day-of-month [months] out from today. */
    private fun monthsFromNow(months: Int): Date {
        val c = Calendar.getInstance()
        c.add(Calendar.MONTH, months)
        c.set(Calendar.HOUR_OF_DAY, 12)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.time
    }

    private fun daysFromNow(days: Int): Date {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, days)
        c.set(Calendar.HOUR_OF_DAY, 12)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.time
    }

    /** The next time [day] rolls around, always in the future. */
    private fun nextOccurrenceOfDay(day: Int): Date {
        val c = Calendar.getInstance()
        val today = c.get(Calendar.DAY_OF_MONTH)
        if (today >= day) c.add(Calendar.MONTH, 1)
        c.set(Calendar.DAY_OF_MONTH, minOf(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
        c.set(Calendar.HOUR_OF_DAY, 12)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.time
    }

    private fun date(year: Int, month: Int, day: Int): Date {
        val c = Calendar.getInstance()
        c.set(year, month - 1, day, 12, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.time
    }

    companion object {
        fun ageOfMoneyDays(transactions: List<TransactionEntity>, lookback: Int = 10, asOf: Date = Date()): Int? {
            val sorted = transactions.filter { !it.date.after(asOf) }.sortedBy { it.date }
            val queue = mutableListOf<Pair<Date, BigDecimal>>()
            val perOutflow = mutableListOf<Double>()

            for (tx in sorted) {
                if (tx.amount > BigDecimal.ZERO) {
                    queue.add(tx.date to tx.amount)
                } else if (tx.amount < BigDecimal.ZERO) {
                    var remaining = tx.amount.abs()
                    var weightedDays = 0.0
                    var consumedTotal = BigDecimal.ZERO
                    while (remaining > BigDecimal.ZERO && queue.isNotEmpty()) {
                        val (inflowDate, inflowRemaining) = queue[0]
                        val consumed = remaining.min(inflowRemaining)
                        val diff = tx.date.time - inflowDate.time
                        val days = (diff / (1000 * 60 * 60 * 24)).toInt()
                        weightedDays += days.toDouble() * consumed.toDouble()
                        consumedTotal = consumedTotal.add(consumed)
                        remaining = remaining.subtract(consumed)
                        val newRemaining = inflowRemaining.subtract(consumed)
                        if (newRemaining == BigDecimal.ZERO) queue.removeAt(0)
                        else queue[0] = inflowDate to newRemaining
                    }
                    if (consumedTotal > BigDecimal.ZERO) {
                        perOutflow.add(weightedDays / consumedTotal.toDouble())
                    }
                }
            }

            if (perOutflow.isEmpty()) return null
            val recent = perOutflow.takeLast(lookback)
            return (recent.sum() / recent.size).toInt()
        }
    }
}

object BudgetRollover {
    private const val KEY = "budgetRolloverEnabled"
    private const val KEY_EXCLUDED = "budgetRolloverExcludedCategoryIDs"

    var isEnabled: Boolean
        get() = prefs?.getBoolean(KEY, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY, value)?.apply() }

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: android.content.Context) {
        prefs = context.getSharedPreferences("truesummit_prefs", android.content.Context.MODE_PRIVATE)
    }

    fun isExcluded(categoryId: java.util.UUID): Boolean {
        val raw = prefs?.getStringSet(KEY_EXCLUDED, emptySet()) ?: return false
        return raw.contains(categoryId.toString())
    }

    fun setExcluded(categoryId: java.util.UUID, excluded: Boolean) {
        val current = prefs?.getStringSet(KEY_EXCLUDED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (excluded) current.add(categoryId.toString()) else current.remove(categoryId.toString())
        prefs?.edit()?.putStringSet(KEY_EXCLUDED, current)?.apply()
    }
}
