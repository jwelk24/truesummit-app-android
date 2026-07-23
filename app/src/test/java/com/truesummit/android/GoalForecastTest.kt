package com.truesummit.android

import com.truesummit.android.data.entity.GoalEntity
import com.truesummit.android.data.model.GoalType
import com.truesummit.android.service.GoalForecast
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.*

private fun assertBDEquals(expected: BigDecimal, actual: BigDecimal?) {
    assertTrue("expected $expected but was $actual", actual != null && expected.compareTo(actual) == 0)
}

class GoalForecastTest {

    private fun byDateGoal(targetAmount: Double, targetDate: Date) = GoalEntity(
        id = UUID.randomUUID(),
        type = GoalType.BY_DATE_TARGET,
        targetAmount = BigDecimal(targetAmount.toString()),
        targetDate = targetDate,
        categoryId = null
    )

    @Test fun spreadsRemainingEvenlyOverMonthsLeft() {
        // $1200 by December, viewed in July: 6 months → $200/month
        val goal = byDateGoal(1200.0, TestSupport.date(2026, 12, 15))
        val needed = GoalForecast.neededThisMonth(
            goal = goal, availableNow = BigDecimal.ZERO, assignedThisMonth = BigDecimal.ZERO,
            currentYear = 2026, currentMonth = 7
        )
        assertBDEquals(BigDecimal("200.00"), needed)
    }

    @Test fun priorProgressReducesMonthlyShare() {
        // $600 already saved → $600 left over 6 months = $100/month
        val goal = byDateGoal(1200.0, TestSupport.date(2026, 12, 15))
        val needed = GoalForecast.neededThisMonth(
            goal = goal, availableNow = BigDecimal("600"), assignedThisMonth = BigDecimal.ZERO,
            currentYear = 2026, currentMonth = 7
        )
        assertBDEquals(BigDecimal("100.00"), needed)
    }

    @Test fun thisMonthAssignmentCountsTowardShare() {
        // Share $200, $150 already assigned this month → $50 more
        val goal = byDateGoal(1200.0, TestSupport.date(2026, 12, 15))
        val needed = GoalForecast.neededThisMonth(
            goal = goal, availableNow = BigDecimal("150"), assignedThisMonth = BigDecimal("150"),
            currentYear = 2026, currentMonth = 7
        )
        assertBDEquals(BigDecimal("50.00"), needed)
    }

    @Test fun fundedMonthNeedsNothing() {
        val goal = byDateGoal(1200.0, TestSupport.date(2026, 12, 15))
        val needed = GoalForecast.neededThisMonth(
            goal = goal, availableNow = BigDecimal("200"), assignedThisMonth = BigDecimal("200"),
            currentYear = 2026, currentMonth = 7
        )
        assertBDEquals(BigDecimal.ZERO, needed)
    }

    @Test fun reachedTargetNeedsNothing() {
        val goal = byDateGoal(1200.0, TestSupport.date(2026, 12, 15))
        val needed = GoalForecast.neededThisMonth(
            goal = goal, availableNow = BigDecimal("1200"), assignedThisMonth = BigDecimal.ZERO,
            currentYear = 2026, currentMonth = 7
        )
        assertBDEquals(BigDecimal.ZERO, needed)
    }

    @Test fun pastDeadlineCollapsesToEverythingDueNow() {
        // Deadline was March, viewing in July → 1 month window → all $800 needed now
        val goal = byDateGoal(900.0, TestSupport.date(2026, 3, 15))
        val needed = GoalForecast.neededThisMonth(
            goal = goal, availableNow = BigDecimal("100"), assignedThisMonth = BigDecimal.ZERO,
            currentYear = 2026, currentMonth = 7
        )
        assertBDEquals(BigDecimal("800.00"), needed)
    }

    @Test fun monthlyShareRoundsUp() {
        // $1000 over 3 months = $333.33… → rounds UP to $333.34
        val goal = byDateGoal(1000.0, TestSupport.date(2026, 9, 15))
        val needed = GoalForecast.neededThisMonth(
            goal = goal, availableNow = BigDecimal.ZERO, assignedThisMonth = BigDecimal.ZERO,
            currentYear = 2026, currentMonth = 7
        )
        assertBDEquals(BigDecimal("333.34"), needed)
    }

    @Test fun nonDateTargetGoalReturnsNull() {
        val goal = GoalEntity(
            id = UUID.randomUUID(),
            type = GoalType.MONTHLY_AMOUNT,
            targetAmount = BigDecimal("500"),
            targetDate = null,
            categoryId = null
        )
        assertNull(GoalForecast.neededThisMonth(
            goal = goal, availableNow = BigDecimal.ZERO, assignedThisMonth = BigDecimal.ZERO,
            currentYear = 2026, currentMonth = 7
        ))
    }
}
