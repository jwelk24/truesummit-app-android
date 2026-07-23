package com.truesummit.android.service

import com.truesummit.android.data.entity.GoalEntity
import com.truesummit.android.data.entity.ScheduledItemEntity
import com.truesummit.android.data.entity.TransactionEntity
import java.math.BigDecimal
import java.util.*

data class PaycheckPlan(
    val billsBeforeNextPaycheck: List<ScheduledItemEntity>,
    val goalItems: List<GoalEntity>,
    val suggestedAmount: BigDecimal,
    val nextPaycheckDate: Date?
)

object PaycheckPlanner {
    fun plan(
        scheduled: List<ScheduledItemEntity>,
        goals: List<GoalEntity>,
        recentTransactions: List<TransactionEntity>,
        now: Date = Date()
    ): PaycheckPlan {
        val msPerDay = 86_400_000L

        // Upcoming income = scheduled items with negative amount (credits)
        val nextIncome = scheduled
            .filter { it.amount < BigDecimal.ZERO && it.nextDate.after(now) }
            .minByOrNull { it.nextDate.time }

        val nextPaycheckDate = nextIncome?.nextDate

        // Bills due before next paycheck (or within 14 days if no paycheck)
        val cutoff = nextPaycheckDate ?: Date(now.time + 14 * msPerDay)
        val billsBeforeNext = scheduled.filter { item ->
            item.amount > BigDecimal.ZERO &&
                item.nextDate.after(now) &&
                !item.nextDate.after(cutoff)
        }.sortedBy { it.nextDate.time }

        val billTotal = billsBeforeNext.sumOf { it.amount }

        // Active goals with a target date in the future
        val activeGoals = goals.filter { g -> g.targetDate == null || g.targetDate.after(now) }

        // Rough per-paycheck contribution across 3 paycheck horizon
        val goalNeeded = activeGoals
            .mapNotNull { g ->
                val daysLeft = g.targetDate?.let { (it.time - now.time) / msPerDay } ?: 90L
                if (daysLeft <= 0) return@mapNotNull null
                // No current amount stored on entity — budget engine tracks that separately;
                // use target as the remaining (conservative)
                g.targetAmount.divide(BigDecimal(maxOf(1L, daysLeft / 14L)))
            }
            .fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }

        val suggested = billTotal.add(goalNeeded)

        return PaycheckPlan(
            billsBeforeNextPaycheck = billsBeforeNext,
            goalItems = activeGoals,
            suggestedAmount = suggested,
            nextPaycheckDate = nextPaycheckDate
        )
    }
}
