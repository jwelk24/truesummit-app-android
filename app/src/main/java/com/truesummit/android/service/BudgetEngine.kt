package com.truesummit.android.service

import android.content.Context
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.*
import com.truesummit.android.data.model.AccountType
import com.truesummit.android.data.model.GoalType
import com.truesummit.android.data.model.ScheduledKind
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

class BudgetEngine(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

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

        val formatters = listOf("yyyy-MM-dd", "MM/dd/yyyy", "yyyy/MM/dd").map { fmt ->
            SimpleDateFormat(fmt, Locale.US)
        }
        fun parseDate(s: String): Date? = formatters.firstNotNullOfOrNull { it.parse(s) }

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

    suspend fun resetAllData() {
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
        seedIfNeeded()
    }

    suspend fun seedIfNeeded() {
        val existing = db.accountDao().getAll().first()
        if (existing.isNotEmpty()) return

        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1

        val checking = AccountEntity(name = "Checking", type = AccountType.CHECKING, balance = BigDecimal("3800"), currencyCode = "USD")
        val savings = AccountEntity(name = "Savings", type = AccountType.SAVINGS, balance = BigDecimal("5000"), currencyCode = "USD")
        val creditCard = AccountEntity(name = "Credit Card", type = AccountType.CREDIT_CARD, balance = BigDecimal("-450"), currencyCode = "USD")
        listOf(checking, savings, creditCard).forEach { db.accountDao().insert(it) }

        val needs = CategoryGroupEntity(name = "Needs (Fixed Expenses)", sort = 0)
        val wants = CategoryGroupEntity(name = "Wants (Flexible Expenses)", sort = 1)
        val savingsDebt = CategoryGroupEntity(name = "Savings & Debt", sort = 2)
        val cardPayments = CategoryGroupEntity(name = "Credit Card Payments", sort = 3)
        listOf(needs, wants, savingsDebt, cardPayments).forEach { db.categoryDao().insertGroup(it) }

        val creditCardCat = CategoryEntity(name = creditCard.name, sort = 0, groupId = cardPayments.id, linkedAccountId = creditCard.id)
        val housing = CategoryEntity(name = "Housing", sort = 0, groupId = needs.id, linkedAccountId = null)
        val utilities = CategoryEntity(name = "Utilities", sort = 1, groupId = needs.id, linkedAccountId = null)
        val groceries = CategoryEntity(name = "Groceries", sort = 2, groupId = needs.id, linkedAccountId = null)
        val transportation = CategoryEntity(name = "Transportation", sort = 3, groupId = needs.id, linkedAccountId = null)
        val insurance = CategoryEntity(name = "Insurance", sort = 4, groupId = needs.id, linkedAccountId = null)
        val dining = CategoryEntity(name = "Dining Out & Entertainment", sort = 0, groupId = wants.id, linkedAccountId = null)
        val subscriptions = CategoryEntity(name = "Subscriptions", sort = 1, groupId = wants.id, linkedAccountId = null)
        val personalCare = CategoryEntity(name = "Personal Care & Clothing", sort = 2, groupId = wants.id, linkedAccountId = null)
        val travel = CategoryEntity(name = "Vacation & Travel", sort = 3, groupId = wants.id, linkedAccountId = null)
        val gifts = CategoryEntity(name = "Gifts & Donations", sort = 4, groupId = wants.id, linkedAccountId = null)
        val debtRepayment = CategoryEntity(name = "Debt Repayment", sort = 0, groupId = savingsDebt.id, linkedAccountId = null)
        val savingsInvestments = CategoryEntity(name = "Savings & Investments", sort = 1, groupId = savingsDebt.id, linkedAccountId = null)
        listOf(creditCardCat, housing, utilities, groceries, transportation, insurance,
            dining, subscriptions, personalCare, travel, gifts, debtRepayment, savingsInvestments)
            .forEach { db.categoryDao().insertCategory(it) }

        val monthRec = BudgetMonthEntity(year = year, month = month, carryover = BigDecimal.ZERO)
        db.budgetDao().insertMonth(monthRec)
        db.budgetDao().insertAllocation(BudgetAllocationEntity(amount = BigDecimal("1800"), categoryId = housing.id, monthId = monthRec.id))
        db.budgetDao().insertAllocation(BudgetAllocationEntity(amount = BigDecimal("300"), categoryId = groceries.id, monthId = monthRec.id))
        db.budgetDao().insertAllocation(BudgetAllocationEntity(amount = BigDecimal("400"), categoryId = savingsInvestments.id, monthId = monthRec.id))

        val now = Date()
        val tx1 = TransactionEntity(date = now, amount = BigDecimal("2500"), merchant = "Employer", memo = null, cleared = true, flagColor = null, pfcPrimary = null, accountId = checking.id, categoryId = null)
        val tx2 = TransactionEntity(date = now, amount = BigDecimal("-120"), merchant = "Whole Foods", memo = null, cleared = true, flagColor = null, pfcPrimary = null, accountId = checking.id, categoryId = groceries.id)
        val tx3 = TransactionEntity(date = now, amount = BigDecimal("-45"), merchant = "Chipotle", memo = null, cleared = true, flagColor = null, pfcPrimary = null, accountId = checking.id, categoryId = dining.id)
        listOf(tx1, tx2, tx3).forEach { db.transactionDao().insert(it) }
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
