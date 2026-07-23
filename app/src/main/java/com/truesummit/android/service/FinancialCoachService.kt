package com.truesummit.android.service

import com.truesummit.android.data.entity.AccountEntity
import com.truesummit.android.data.entity.ScheduledItemEntity
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.data.model.ScheduledKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

enum class CoachSentiment { POSITIVE, NEGATIVE, WARNING, NEUTRAL }

data class CoachInsight(
    val id: UUID = UUID.randomUUID(),
    val icon: String,
    val title: String,
    val detail: String,
    val sentiment: CoachSentiment
)

object FinancialCoachService {

    fun insights(
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        scheduled: List<ScheduledItemEntity>,
        cushion: BigDecimal,
        now: Date = Date()
    ): List<CoachInsight> {
        val out = mutableListOf<CoachInsight>()
        out += cashFlowInsight(accounts, scheduled, transactions, cushion, now)
        out += streakInsight(transactions, now)
        out += categoryMovers(transactions, now)
        out += priceChangeInsights(transactions, now)
        out += upcomingBillInsight(scheduled, now)
        return out.take(5)
    }

    // MARK: Cash-flow tight warning

    private fun cashFlowInsight(
        accounts: List<AccountEntity>,
        scheduled: List<ScheduledItemEntity>,
        transactions: List<TransactionEntity>,
        cushion: BigDecimal,
        now: Date
    ): List<CoachInsight> {
        val safe = SafeToSpendService.compute(accounts, scheduled, transactions, cushion, now)
        if (!safe.hasSpendableAccount) return emptyList()
        if (!safe.isTight) return emptyList()
        val until = safe.nextIncomeDate?.let {
            val cal = Calendar.getInstance(); cal.time = it
            " until ${cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())} ${cal.get(Calendar.DAY_OF_MONTH)}"
        } ?: ""
        return listOf(CoachInsight(
            icon = "warning",
            title = "Money's tight right now",
            detail = "Upcoming bills leave little room$until. Keep discretionary spending low to stay above your ${formatCurrency(cushion)} cushion.",
            sentiment = CoachSentiment.WARNING
        ))
    }

    // MARK: No-spend streak

    private fun streakInsight(transactions: List<TransactionEntity>, now: Date): List<CoachInsight> {
        val cal = Calendar.getInstance()
        val today = startOfDay(now)
        val spendDays = transactions
            .filter { it.amount < BigDecimal.ZERO }
            .map { startOfDay(it.date) }.toHashSet()
        var streak = 0
        var day = today
        while (!spendDays.contains(day) && streak <= 120) {
            streak++
            cal.time = day; cal.add(Calendar.DAY_OF_YEAR, -1); day = cal.time
        }
        if (streak < 2) return emptyList()
        return listOf(CoachInsight(
            icon = "flame",
            title = "$streak-day no-spend streak",
            detail = "No spending logged for $streak days straight — keep it going.",
            sentiment = CoachSentiment.POSITIVE
        ))
    }

    // MARK: Category month-over-month movers

    private fun categoryMovers(transactions: List<TransactionEntity>, now: Date): List<CoachInsight> {
        val cal = Calendar.getInstance()
        cal.time = now
        val curYear = cal.get(Calendar.YEAR)
        val curMonth = cal.get(Calendar.MONTH)
        val dayOffset = cal.get(Calendar.DAY_OF_MONTH)

        cal.set(curYear, curMonth, 1, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
        val startThis = cal.time
        cal.add(Calendar.MONTH, -1)
        val startLast = cal.time
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        val endLast = cal.time

        fun spendByCategory(from: Date, toExclusive: Date): Map<String, BigDecimal> {
            val totals = mutableMapOf<String, BigDecimal>()
            for (tx in transactions) {
                if (tx.date < from || tx.date >= toExclusive || tx.amount >= BigDecimal.ZERO) continue
                val name = tx.categoryId?.toString() ?: "Uncategorized"
                totals[name] = (totals[name] ?: BigDecimal.ZERO).add(tx.amount.negate())
            }
            return totals
        }

        val thisMTD = spendByCategory(startThis, now)
        val lastMTD = spendByCategory(startLast, endLast)

        data class Mover(val name: String, val thisPeriod: BigDecimal, val lastPeriod: BigDecimal, val diff: BigDecimal, val ratio: Double)
        val movers = (thisMTD.keys + lastMTD.keys).toSet().mapNotNull { name ->
            val thisAmt = thisMTD[name] ?: BigDecimal.ZERO
            val lastAmt = lastMTD[name] ?: BigDecimal.ZERO
            if (lastAmt < BigDecimal("50")) return@mapNotNull null
            val diff = thisAmt.subtract(lastAmt)
            if (diff.abs() < BigDecimal("30")) return@mapNotNull null
            val ratio = diff.toDouble() / lastAmt.toDouble()
            if (kotlin.math.abs(ratio) < 0.25) return@mapNotNull null
            Mover(name, thisAmt, lastAmt, diff, ratio)
        }.sortedByDescending { it.diff.abs() }

        return movers.take(2).map { m ->
            val pct = (kotlin.math.abs(m.ratio) * 100).toInt()
            if (m.diff > BigDecimal.ZERO) {
                CoachInsight(icon = "trending_up", title = "${m.name} is up $pct% this month",
                    detail = "${formatCurrency(m.lastPeriod)} → ${formatCurrency(m.thisPeriod)} versus the same point last month.",
                    sentiment = CoachSentiment.NEGATIVE)
            } else {
                CoachInsight(icon = "trending_down", title = "${m.name} is down $pct% this month",
                    detail = "${formatCurrency(m.lastPeriod)} → ${formatCurrency(m.thisPeriod)} versus the same point last month. Nice work.",
                    sentiment = CoachSentiment.POSITIVE)
            }
        }
    }

    // MARK: Subscription price changes

    private fun priceChangeInsights(transactions: List<TransactionEntity>, now: Date): List<CoachInsight> {
        return SubscriptionTracker.detectPriceChanges(transactions, now = now).take(2).map { change ->
            CoachInsight(
                icon = if (change.isIncrease) "arrow_upward" else "arrow_downward",
                title = "${change.merchant} ${if (change.isIncrease) "raised" else "lowered"} its price",
                detail = "${formatCurrency(change.oldAmount)} → ${formatCurrency(change.newAmount)} per charge.",
                sentiment = if (change.isIncrease) CoachSentiment.NEGATIVE else CoachSentiment.POSITIVE
            )
        }
    }

    // MARK: Upcoming large bill

    private fun upcomingBillInsight(scheduled: List<ScheduledItemEntity>, now: Date): List<CoachInsight> {
        val cal = Calendar.getInstance()
        val today = startOfDay(now)
        cal.time = today; cal.add(Calendar.DAY_OF_YEAR, 7)
        val horizon = cal.time
        val bill = scheduled
            .filter { (it.kind == ScheduledKind.BILL || it.kind == ScheduledKind.SUBSCRIPTION) && it.nextDate >= today && it.nextDate <= horizon }
            .maxByOrNull { it.amount.abs() }
        if (bill == null || bill.amount.abs() < BigDecimal("200")) return emptyList()
        val days = ((startOfDay(bill.nextDate).time - today.time) / (1000L * 60 * 60 * 24)).toInt()
        val when_ = when {
            days <= 0 -> "today"
            days == 1 -> "tomorrow"
            else -> "in $days days"
        }
        return listOf(CoachInsight(
            icon = "event_busy",
            title = "Big bill coming up",
            detail = "${bill.name} — ${formatCurrency(bill.amount.abs())} due $when_.",
            sentiment = CoachSentiment.WARNING
        ))
    }

    private fun startOfDay(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun formatCurrency(d: BigDecimal): String {
        val n = d.setScale(0, RoundingMode.HALF_UP).toLong()
        return if (n < 0) "-\$${-n}" else "\$$n"
    }
}
