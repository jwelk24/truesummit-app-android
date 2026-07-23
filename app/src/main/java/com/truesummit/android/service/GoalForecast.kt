package com.truesummit.android.service

import com.truesummit.android.data.entity.GoalEntity
import com.truesummit.android.data.model.GoalType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar

sealed class GoalPace {
    object Reached : GoalPace()
    data class OnTrack(val monthsEarly: Int) : GoalPace()
    data class Behind(val monthsLate: Int) : GoalPace()
    object Unfunded : GoalPace()
    data class ShortThisMonth(val amountNeeded: BigDecimal) : GoalPace()
    data class Projecting(val monthsToGoal: Int) : GoalPace()
    object FundedThisMonth : GoalPace()
    data class NeedToStayOnTrack(val amountNeeded: BigDecimal) : GoalPace()
}

object GoalForecast {

    /**
     * Compute pacing for a goal. Pure — all inputs are pre-fetched by the caller.
     *
     * @param goal the goal entity
     * @param assignedThisMonth amount assigned to the category this month
     * @param availableNow total available in the category (assigned + activity)
     * @param avgMonthlyAssigned 3-month average assigned (used for savings/projection goals)
     * @param currentYear the budget month being viewed
     * @param currentMonth the budget month being viewed (1-based)
     */
    fun pace(
        goal: GoalEntity,
        assignedThisMonth: BigDecimal,
        availableNow: BigDecimal,
        avgMonthlyAssigned: BigDecimal,
        currentYear: Int,
        currentMonth: Int
    ): GoalPace {
        return when (goal.type) {
            GoalType.MONTHLY_AMOUNT -> {
                if (assignedThisMonth >= goal.targetAmount) GoalPace.Reached
                else GoalPace.ShortThisMonth(goal.targetAmount - assignedThisMonth)
            }

            GoalType.SAVINGS_TARGET -> {
                val remaining = goal.targetAmount - availableNow.max(BigDecimal.ZERO)
                if (remaining <= BigDecimal.ZERO) return GoalPace.Reached
                if (avgMonthlyAssigned <= BigDecimal.ZERO) return GoalPace.Unfunded
                val monthsToGoal = (remaining.toDouble() / avgMonthlyAssigned.toDouble())
                    .let { Math.ceil(it).toInt() }.coerceAtLeast(1)
                GoalPace.Projecting(monthsToGoal)
            }

            GoalType.BY_DATE_TARGET -> {
                val remaining = goal.targetAmount - availableNow.max(BigDecimal.ZERO)
                if (remaining <= BigDecimal.ZERO) return GoalPace.Reached

                val targetDate = goal.targetDate
                if (targetDate == null) {
                    if (avgMonthlyAssigned <= BigDecimal.ZERO) return GoalPace.Unfunded
                    val monthsToGoal = (remaining.toDouble() / avgMonthlyAssigned.toDouble())
                        .let { Math.ceil(it).toInt() }.coerceAtLeast(1)
                    return GoalPace.Projecting(monthsToGoal)
                }

                val needed = neededThisMonth(
                    goal = goal,
                    availableNow = availableNow,
                    assignedThisMonth = assignedThisMonth,
                    currentYear = currentYear,
                    currentMonth = currentMonth
                ) ?: BigDecimal.ZERO

                if (needed > BigDecimal.ZERO) GoalPace.NeedToStayOnTrack(needed)
                else GoalPace.FundedThisMonth
            }
        }
    }

    /**
     * For BY_DATE_TARGET: how much more to assign this month to stay on track.
     * Returns null for other goal types. Returns 0 when already funded for this month.
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
        val priorProgress = (availableNow - assignedThisMonth).max(BigDecimal.ZERO)
        val stillNeeded = goal.targetAmount - priorProgress
        if (stillNeeded <= BigDecimal.ZERO) return BigDecimal.ZERO
        val perMonth = stillNeeded.divide(BigDecimal(monthsLeft), 2, RoundingMode.UP)
        return (perMonth - assignedThisMonth).max(BigDecimal.ZERO)
    }
}
