package com.truesummit.android

import com.truesummit.android.service.BudgetEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests the pure (non-Room) methods on BudgetEngine companion / static functions. */
class BudgetMathTest {

    // ── ageOfMoneyDays ───────────────────────────────────────────────────────

    @Test fun ageOfMoneyDaysIsNullWithNoTransactions() {
        assertNull(BudgetEngine.ageOfMoneyDays(emptyList()))
    }

    @Test fun ageOfMoneyDaysIsNullWithOnlySpending() {
        val txs = listOf(
            TestSupport.tx(TestSupport.date(2026, 6, 5), -100.0)
        )
        assertNull(BudgetEngine.ageOfMoneyDays(txs))
    }

    @Test fun ageOfMoneyDaysIsZeroForSameDayInflowOutflow() {
        val d = TestSupport.date(2026, 6, 5)
        val txs = listOf(
            TestSupport.tx(d, 100.0),
            TestSupport.tx(d, -100.0)
        )
        val age = BudgetEngine.ageOfMoneyDays(txs)
        assertEquals(0, age)
    }

    @Test fun ageOfMoneyDaysMatchesElapsedDays() {
        val inflow = TestSupport.date(2026, 6, 1)
        val outflow = TestSupport.date(2026, 6, 11)
        val txs = listOf(
            TestSupport.tx(inflow, 100.0),
            TestSupport.tx(outflow, -100.0)
        )
        // money sat for 10 days
        val age = BudgetEngine.ageOfMoneyDays(txs, asOf = outflow)
        assertEquals(10, age)
    }
}
