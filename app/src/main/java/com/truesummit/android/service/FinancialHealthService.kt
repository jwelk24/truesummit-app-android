package com.truesummit.android.service

import android.content.Context
import com.truesummit.android.data.entity.AccountEntity
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.data.model.AccountType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

data class HealthPillar(
    val id: String,
    val name: String,
    val icon: String,
    /** 0.0–1.0 closeness to ideal */
    val fraction: Double,
    val maxPoints: Int,
    val valueText: String,
    val advice: String
) {
    val points: Int get() = (fraction * maxPoints).toInt()
    val tintKey: String get() = when {
        fraction >= 0.75 -> "green"
        fraction >= 0.40 -> "orange"
        else -> "red"
    }
}

data class FinancialHealthScore(
    val pillars: List<HealthPillar>,
    val hasData: Boolean
) {
    val total: Int get() = pillars.sumOf { it.points }
    val grade: String get() = when {
        total >= 80 -> "Excellent"
        total >= 65 -> "Good"
        total >= 45 -> "Fair"
        else -> "Needs Work"
    }
    val tintKey: String get() = when {
        total >= 80 -> "green"
        total >= 65 -> "mint"
        total >= 45 -> "orange"
        else -> "red"
    }
}

data class HealthScorePoint(val id: Int, val label: String, val score: Int)

object FinancialHealthService {

    /**
     * Scores the last 3 months of activity:
     * savings rate (30) + emergency runway (30) + credit card debt (25) +
     * subscription load (15) = 100.
     */
    fun compute(
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        now: Date = Date(),
        context: Context? = null
    ): FinancialHealthScore {
        val cal = Calendar.getInstance()
        cal.time = now
        cal.add(Calendar.MONTH, -3)
        val start = cal.time
        val period = ReportPeriod(start, now)
        val summary = ReportsService.buildSummary(transactions, period, emptyMap())

        if (summary.totalIncome <= BigDecimal.ZERO) {
            return FinancialHealthScore(emptyList(), false)
        }

        val monthlyIncome = summary.totalIncome.divide(BigDecimal(3), 2, RoundingMode.HALF_UP)
        val monthlySpend = summary.totalSpending.divide(BigDecimal(3), 2, RoundingMode.HALF_UP)

        return FinancialHealthScore(
            pillars = listOf(
                savingsPillar(summary),
                runwayPillar(accounts, monthlySpend),
                debtPillar(accounts, monthlyIncome),
                subscriptionPillar(transactions, monthlyIncome, now, context)
            ),
            hasData = true
        )
    }

    fun scoreHistory(transactions: List<TransactionEntity>, accounts: List<AccountEntity>, now: Date = Date(), context: Context? = null): List<HealthScorePoint> {
        val cal = Calendar.getInstance()
        val result = mutableListOf<HealthScorePoint>()
        for (offset in 5 downTo 0) {
            cal.time = now
            cal.add(Calendar.MONTH, -offset)
            val snap = compute(transactions, accounts, cal.time)
            if (!snap.hasData) continue
            val label = String.format("%tB", cal).take(3)
            result.add(HealthScorePoint(id = 5 - offset, label = label, score = snap.total))
        }
        return result
    }

    // MARK: Savings rate — 20%+ earns full marks

    private fun savingsPillar(summary: ReportSummary): HealthPillar {
        val rate = if (summary.totalIncome > BigDecimal.ZERO)
            summary.netWorthChange.toDouble() / summary.totalIncome.toDouble()
        else 0.0
        val fraction = clamp(rate / 0.20)
        val pct = (rate * 100).toInt()
        val advice = when {
            fraction >= 1.0 -> "You're keeping $pct% of your income — at or above the 20% benchmark."
            rate > 0 -> "You're keeping $pct% of your income. Nudging toward 20% strengthens everything else."
            else -> "You're spending more than you earn right now. Getting back to break-even is the first step."
        }
        return HealthPillar("savings", "Savings Rate", "trending_up", fraction, 30, "$pct%", advice)
    }

    // MARK: Emergency runway — 6 months of expenses earns full marks

    private fun runwayPillar(accounts: List<AccountEntity>, monthlySpend: BigDecimal): HealthPillar {
        val liquid = accounts
            .filter { it.type == AccountType.CHECKING || it.type == AccountType.SAVINGS }
            .fold(BigDecimal.ZERO) { acc, a -> acc.add(a.balance.max(BigDecimal.ZERO)) }
        val months = if (monthlySpend > BigDecimal.ZERO) liquid.toDouble() / monthlySpend.toDouble()
        else if (liquid > BigDecimal.ZERO) 6.0 else 0.0
        val fraction = clamp(months / 6.0)
        val monthsText = if (months >= 12) "12+ months" else String.format("%.1f months", months)
        val advice = when {
            fraction >= 1.0 -> "Your cash covers $monthsText of expenses — a full emergency fund."
            months >= 3.0 -> "Your cash covers $monthsText of expenses. Six months is the classic safety target."
            else -> "Your cash covers $monthsText of expenses. Building toward 3–6 months protects you from surprises."
        }
        return HealthPillar("runway", "Emergency Fund", "shield", fraction, 30, monthsText, advice)
    }

    // MARK: Credit card debt — zero debt earns full marks; 1 month income = none

    private fun debtPillar(accounts: List<AccountEntity>, monthlyIncome: BigDecimal): HealthPillar {
        val debt = accounts
            .filter { it.type == AccountType.CREDIT_CARD }
            .fold(BigDecimal.ZERO) { acc, a -> acc.add(a.balance.negate().max(BigDecimal.ZERO)) }
        val ratio = if (monthlyIncome > BigDecimal.ZERO) debt.toDouble() / monthlyIncome.toDouble() else 0.0
        val fraction = clamp(1.0 - ratio)
        val advice = when {
            debt == BigDecimal.ZERO -> "No credit card balance — exactly where you want to be."
            fraction >= 0.5 -> "You're carrying ${formatCurrency(debt)} on cards. Clearing it avoids the most expensive interest there is."
            else -> "Card balances total ${formatCurrency(debt)} — near or above a month of income. Paying this down is the highest-impact move available."
        }
        return HealthPillar("debt", "Card Debt", "credit_card", fraction, 25, formatCurrency(debt), advice)
    }

    // MARK: Subscription load — under 5% earns full marks, 15%+ = none

    private fun subscriptionPillar(transactions: List<TransactionEntity>, monthlyIncome: BigDecimal, now: Date, context: Context? = null): HealthPillar {
        val subs = if (context != null) SubscriptionTracker.detect(transactions, context = context) else emptyList()
        val monthlyCost = subs.fold(BigDecimal.ZERO) { acc, sub ->
            acc.add(sub.typicalAmount.multiply(BigDecimal(30)).divide(BigDecimal(sub.cadence.intervalDays), 2, RoundingMode.HALF_UP))
        }
        val share = if (monthlyIncome > BigDecimal.ZERO) monthlyCost.toDouble() / monthlyIncome.toDouble() else 0.0
        val fraction = clamp(1.0 - (share - 0.05) / 0.10)
        val pct = (share * 100).toInt()
        val advice = if (fraction >= 1.0)
            "Subscriptions run about ${formatCurrency(monthlyCost)}/month — a light footprint."
        else
            "Subscriptions run about ${formatCurrency(monthlyCost)}/month ($pct% of income). Worth an audit for ones you've stopped using."
        return HealthPillar("subscriptions", "Subscriptions", "repeat", fraction, 15, "${formatCurrency(monthlyCost)}/mo", advice)
    }

    private fun clamp(v: Double) = minOf(maxOf(v, 0.0), 1.0)

    private fun formatCurrency(d: BigDecimal): String {
        val n = d.setScale(0, RoundingMode.HALF_UP).toLong()
        return if (n < 0) "-\$${-n}" else "\$$n"
    }
}
