package com.truesummit.android.service

import android.content.Context
import com.truesummit.android.data.entity.TransactionEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import kotlin.math.abs

enum class SubscriptionCadence(val displayName: String, val intervalDays: Int, val matchTolerance: Int) {
    WEEKLY("Weekly", 7, 2),
    BIWEEKLY("Biweekly", 14, 3),
    MONTHLY("Monthly", 30, 5),
    QUARTERLY("Quarterly", 90, 10),
    YEARLY("Yearly", 365, 21)
}

data class DetectedSubscription(
    val id: UUID = UUID.randomUUID(),
    val merchant: String,
    val typicalAmount: BigDecimal,
    val cadence: SubscriptionCadence,
    val occurrences: List<TransactionEntity>,
    val lastChargeDate: Date,
    val predictedNextDate: Date,
    val totalSpend: BigDecimal
)

object SubscriptionTracker {
    private const val PREFS_NAME = "subscription_tracker_prefs"
    private const val IGNORED_MERCHANTS_KEY = "ignored_merchants"

    fun detect(
        transactions: List<TransactionEntity>,
        minOccurrences: Int = 3,
        context: Context,
        now: Date = Date()
    ): List<DetectedSubscription> = detectRecurring(transactions, isInflow = false, minOccurrences, context, now)

    fun detectIncome(
        transactions: List<TransactionEntity>,
        minOccurrences: Int = 3,
        context: Context,
        now: Date = Date()
    ): List<DetectedSubscription> = detectRecurring(transactions, isInflow = true, minOccurrences, context, now)

    private fun detectRecurring(
        transactions: List<TransactionEntity>,
        isInflow: Boolean,
        minOccurrences: Int,
        context: Context,
        now: Date
    ): List<DetectedSubscription> {
        val ignored = getIgnoredMerchants(context)

        val filtered = transactions.filter {
            it.merchant.isNotBlank() && if (isInflow) it.amount > BigDecimal.ZERO else it.amount < BigDecimal.ZERO
        }
        val grouped = filtered.groupBy { canonicalMerchant(it.merchant) }

        val results = mutableListOf<DetectedSubscription>()
        for ((canonical, rawTxs) in grouped) {
            if (ignored.contains(canonical)) continue
            if (rawTxs.size < minOccurrences) continue

            val sorted = rawTxs.sortedBy { it.date }
            val detected = match(sorted, canonical, now)
            if (detected != null) {
                results.add(detected)
            }
        }

        return results.sortedByDescending { it.totalSpend }
    }

    private fun match(
        sortedTransactions: List<TransactionEntity>,
        canonical: String,
        now: Date
    ): DetectedSubscription? {
        // Intervals in days
        val intervals = mutableListOf<Int>()
        for (i in 1 until sortedTransactions.size) {
            val diff = sortedTransactions[i].date.time - sortedTransactions[i - 1].date.time
            val days = (diff / (1000 * 60 * 60 * 24)).toInt()
            if (days > 0) intervals.add(days)
        }
        if (intervals.isEmpty()) return null
        val medianInterval = median(intervals.map { it.toDouble() })

        // Match to cadence
        val cadence = SubscriptionCadence.values().firstOrNull {
            abs(it.intervalDays - medianInterval) <= it.matchTolerance
        } ?: return null

        // Amount stability (+/- 15% of median)
        val absAmounts = sortedTransactions.map { it.amount.abs() }
        val medianAmount = medianBigDecimal(absAmounts)
        if (medianAmount <= BigDecimal.ZERO) return null

        val withinSpread = absAmounts.all { amount ->
            val diff = (amount - medianAmount).abs()
            val spread = diff.divide(medianAmount, 4, RoundingMode.HALF_UP).toDouble()
            spread <= 0.15
        }
        if (!withinSpread) return null

        val last = sortedTransactions.last()
        val calendar = Calendar.getInstance()
        calendar.time = last.date
        calendar.add(Calendar.DAY_OF_YEAR, cadence.intervalDays)
        val predictedNext = calendar.time

        // Stale guard (2x cadence)
        calendar.time = last.date
        calendar.add(Calendar.DAY_OF_YEAR, cadence.intervalDays * 2)
        if (calendar.time.before(now)) return null

        val totalSpend = absAmounts.fold(BigDecimal.ZERO) { acc, amount -> acc + amount }
        val displayMerchant = mostCommonDisplayName(sortedTransactions.map { it.merchant }) ?: canonical

        return DetectedSubscription(
            merchant = displayMerchant,
            typicalAmount = medianAmount,
            cadence = cadence,
            occurrences = sortedTransactions.reversed(),
            lastChargeDate = last.date,
            predictedNextDate = predictedNext,
            totalSpend = totalSpend
        )
    }

    data class DetectedPriceChange(
        val id: java.util.UUID = java.util.UUID.randomUUID(),
        val merchant: String,
        val cadence: SubscriptionCadence,
        val oldAmount: BigDecimal,
        val newAmount: BigDecimal,
        val changeDate: java.util.Date
    ) {
        val delta: BigDecimal get() = newAmount - oldAmount
        val isIncrease: Boolean get() = newAmount > oldAmount
    }

    fun detectPriceChanges(
        transactions: List<TransactionEntity>,
        minOccurrences: Int = 3,
        now: java.util.Date = java.util.Date()
    ): List<DetectedPriceChange> {
        val outflows = transactions.filter { it.amount < BigDecimal.ZERO && it.merchant.isNotBlank() }
        val grouped = outflows.groupBy { canonicalMerchant(it.merchant) }
        val results = mutableListOf<DetectedPriceChange>()

        for ((canonical, raw) in grouped) {
            if (raw.size < minOccurrences) continue
            val sorted = raw.sortedBy { it.date }

            val intervals = mutableListOf<Int>()
            for (i in 1 until sorted.size) {
                val days = ((sorted[i].date.time - sorted[i - 1].date.time) / (1000 * 60 * 60 * 24)).toInt()
                if (days > 0) intervals.add(days)
            }
            if (intervals.isEmpty()) continue
            val medianInterval = median(intervals.map { it.toDouble() })
            val cadence = SubscriptionCadence.values().firstOrNull {
                kotlin.math.abs(it.intervalDays - medianInterval) <= it.matchTolerance
            } ?: continue

            val latest = sorted.last()
            val staleCal = java.util.Calendar.getInstance().apply { time = latest.date; add(java.util.Calendar.DAY_OF_YEAR, cadence.intervalDays * 2) }
            if (staleCal.time.before(now)) continue

            val previous = sorted.dropLast(1)
            if (previous.size < 2) continue
            val prevAbs = previous.map { it.amount.abs() }
            val prevTypical = medianBigDecimal(prevAbs)
            if (prevTypical <= BigDecimal.ZERO) continue

            val stable = prevAbs.all { amt ->
                val diff = (amt - prevTypical).abs()
                diff.divide(prevTypical, 4, java.math.RoundingMode.HALF_UP).toDouble() <= 0.10
            }
            if (!stable) continue

            val latestAbs = latest.amount.abs()
            val diff = (latestAbs - prevTypical).abs()
            val ratio = diff.divide(prevTypical, 4, java.math.RoundingMode.HALF_UP).toDouble()
            if (ratio <= 0.05 || diff < BigDecimal.ONE) continue

            val display = mostCommonDisplayName(sorted.map { it.merchant }) ?: canonical
            results.add(DetectedPriceChange(
                merchant = display,
                cadence = cadence,
                oldAmount = prevTypical,
                newAmount = latestAbs,
                changeDate = latest.date
            ))
        }
        return results.sortedByDescending { it.changeDate }
    }

    fun canonicalMerchant(merchant: String): String {
        return merchant.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun mostCommonDisplayName(names: List<String>): String? {
        return names.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun medianBigDecimal(values: List<BigDecimal>): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]).divide(BigDecimal(2))
        } else sorted[mid]
    }

    fun ignore(context: Context, merchant: String) {
        val ignored = getIgnoredMerchants(context).toMutableSet()
        ignored.add(canonicalMerchant(merchant))
        saveIgnoredMerchants(context, ignored)
    }

    fun restore(context: Context, merchant: String) {
        val ignored = getIgnoredMerchants(context).toMutableSet()
        ignored.remove(canonicalMerchant(merchant))
        saveIgnoredMerchants(context, ignored)
    }

    fun getIgnoredMerchants(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(IGNORED_MERCHANTS_KEY, emptySet()) ?: emptySet()
    }

    private fun saveIgnoredMerchants(context: Context, ignored: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(IGNORED_MERCHANTS_KEY, ignored).apply()
    }
}
