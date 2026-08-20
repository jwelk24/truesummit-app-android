package com.truesummit.android.widget

import android.content.Context
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.model.AccountType
import com.truesummit.android.service.BudgetEngine
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

data class AccountSummary(
    val id: UUID,
    val name: String,
    val type: AccountType,
    val balance: BigDecimal
)

data class BillSummary(
    val id: UUID,
    val name: String,
    val amount: BigDecimal,
    val date: Date
)

data class TrueSummitSnapshot(
    val lastUpdated: Date,
    val currencyCode: String,
    val totalAssets: BigDecimal,
    val totalLiabilities: BigDecimal,
    val accounts: List<AccountSummary>,
    val monthLabel: String,
    val budgetAssigned: BigDecimal,
    val budgetSpent: BigDecimal,
    val upcomingBills: List<BillSummary>
) {
    val netWorth: BigDecimal get() = totalAssets.subtract(totalLiabilities)
    val budgetRemaining: BigDecimal get() = budgetAssigned.subtract(budgetSpent)
    val budgetUsedFraction: Float get() = if (budgetAssigned > BigDecimal.ZERO) {
        budgetSpent.divide(budgetAssigned, 2, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
    } else 0f

    companion object {
        suspend fun build(context: Context): TrueSummitSnapshot {
            val db = AppDatabase.getInstance(context.applicationContext)
            val accounts = db.accountDao().getAll().first()
            val txs = db.transactionDao().getAll().first()
            val scheduled = db.scheduledItemDao().getAll().first()

            val now = Date()
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1

            val budgetMonth = db.budgetDao().getMonth(year, month)
            val allocations = budgetMonth?.let { db.budgetDao().getAllocationsForMonth(it.id).first() } ?: emptyList()

            var totalAssets = BigDecimal.ZERO
            var totalLiabilities = BigDecimal.ZERO
            for (a in accounts) {
                if (a.type.isAsset) totalAssets = totalAssets.add(a.balance)
                else totalLiabilities = totalLiabilities.add(a.balance.abs())
            }

            val accountSummaries = accounts.sortedBy { it.name }.map {
                AccountSummary(it.id, it.name, it.type, it.balance)
            }

            val assignedTotal = allocations.fold(BigDecimal.ZERO) { acc, alloc -> acc.add(alloc.amount) }

            // Spent goes through the same engine the Budget screen uses, so every
            // surface reports the same number. Summing raw negative transactions
            // counts uncategorized spending, drops refunds, and ignores splits.
            val categories = db.categoryDao().getCategories().first()
            val engine = BudgetEngine(context)
            val spentTotal = categories
                .fold(BigDecimal.ZERO) { acc, cat -> acc.add(engine.activity(cat, year, month)) }
                .abs()

            val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(now)

            val in30Days = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 30) }.time
            val upcoming = scheduled.filter { 
                it.amount < BigDecimal.ZERO && it.nextDate.after(now) && it.nextDate.before(in30Days) 
            }.sortedBy { it.nextDate }.take(6).map {
                BillSummary(it.id, it.name, it.amount, it.nextDate)
            }

            return TrueSummitSnapshot(
                lastUpdated = now,
                currencyCode = accounts.firstOrNull()?.currencyCode ?: "USD",
                totalAssets = totalAssets,
                totalLiabilities = totalLiabilities,
                accounts = accountSummaries,
                monthLabel = monthLabel,
                budgetAssigned = assignedTotal,
                budgetSpent = spentTotal,
                upcomingBills = upcoming
            )
        }
    }
}
