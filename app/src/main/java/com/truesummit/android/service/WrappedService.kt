package com.truesummit.android.service

import com.truesummit.android.data.entity.TransactionEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

data class WrappedStats(
    val year: Int,
    val isPartialYear: Boolean,
    val transactionCount: Int,
    val totalSpent: BigDecimal,
    val totalIncome: BigDecimal,
    val topCategories: List<Pair<String, BigDecimal>>,
    val topMerchant: Triple<String, Int, BigDecimal>?,  // name, count, total
    val biggestPurchase: Triple<String, BigDecimal, Date>?,  // merchant, amount, date
    val noSpendDays: Int,
    val longestNoSpendStreak: Int,
    val busiestMonth: Pair<String, BigDecimal>?
) {
    val saved: BigDecimal get() = totalIncome.subtract(totalSpent)
    val savingsRate: Double? get() {
        if (totalIncome <= BigDecimal.ZERO) return null
        return saved.toDouble() / totalIncome.toDouble()
    }
}

object WrappedService {

    private val MONTH_NAMES = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    fun compute(transactions: List<TransactionEntity>, year: Int, now: Date = Date()): WrappedStats {
        val cal = Calendar.getInstance()

        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
        val yearStart = cal.time
        cal.set(year + 1, Calendar.JANUARY, 1, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
        val nextYearStart = cal.time

        val end = minOf(nextYearStart, now)
        val inYear = transactions.filter { it.date >= yearStart && it.date < end }
        val expenses = inYear.filter { it.amount < BigDecimal.ZERO }
        val income = inYear.filter { it.amount > BigDecimal.ZERO }

        val totalSpent = expenses.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.negate()) }
        val totalIncome = income.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }

        // Top 5 categories by spend
        val byCategory = expenses
            .groupBy { it.categoryId?.toString() ?: "Uncategorized" }
            .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.negate()) } }
            .entries.sortedByDescending { it.value }.take(5)
            .map { Pair(it.key, it.value) }

        // Top merchant by visit count
        val byMerchant = expenses.groupBy { MerchantCleaner.clean(it.merchant).lowercase() }
        val topMerchant = byMerchant
            .map { (name, txs) ->
                Triple(
                    name.replaceFirstChar { it.uppercase() },
                    txs.size,
                    txs.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.negate()) }
                )
            }
            .filter { it.first.isNotBlank() }
            .maxByOrNull { it.second }

        // Biggest single purchase
        val biggest = expenses.maxByOrNull { it.amount.negate() }?.let {
            Triple(MerchantCleaner.clean(it.merchant), it.amount.negate(), it.date)
        }

        // No-spend days + longest streak
        val spendDays = expenses.map { startOfDay(it.date) }.toHashSet()
        var noSpend = 0
        var longestStreak = 0
        var currentStreak = 0
        var day = startOfDay(yearStart)
        val lastDay = startOfDay(end)
        while (day < lastDay) {
            if (spendDays.contains(day)) {
                currentStreak = 0
            } else {
                noSpend++
                currentStreak++
                if (currentStreak > longestStreak) longestStreak = currentStreak
            }
            cal.time = day; cal.add(Calendar.DAY_OF_YEAR, 1); day = cal.time
        }

        // Busiest spending month
        val byMonth = expenses
            .groupBy { cal.also { c -> c.time = it.date }.get(Calendar.MONTH) }
            .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.negate()) } }
        val busiest = byMonth.maxByOrNull { it.value }?.let { Pair(MONTH_NAMES[it.key], it.value) }

        return WrappedStats(
            year = year,
            isPartialYear = nextYearStart > now,
            transactionCount = inYear.size,
            totalSpent = totalSpent,
            totalIncome = totalIncome,
            topCategories = byCategory,
            topMerchant = topMerchant,
            biggestPurchase = biggest,
            noSpendDays = noSpend,
            longestNoSpendStreak = longestStreak,
            busiestMonth = busiest
        )
    }

    private fun startOfDay(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }
}
