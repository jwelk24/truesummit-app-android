package com.truesummit.android

import com.truesummit.android.service.ReportPeriod
import com.truesummit.android.service.ReportsService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.*

class ReportBuilderTest {

    private val juneStart = TestSupport.date(2026, 6, 1, hour = 0)
    private val juneEnd = TestSupport.date(2026, 6, 30, hour = 23)
    private val june = ReportPeriod(juneStart, juneEnd)

    @Test fun incomeIsPositiveAmounts() {
        val txs = listOf(
            TestSupport.tx(TestSupport.date(2026, 6, 5), 3000.0, pfcPrimary = "INCOME"),
            TestSupport.tx(TestSupport.date(2026, 6, 10), -1200.0)
        )
        val summary = ReportsService.buildSummary(txs, june, emptyMap())
        assertEquals(BigDecimal("3000.0"), summary.totalIncome)
        assertEquals(BigDecimal("1200.0"), summary.totalSpending)
    }

    @Test fun totalsSeparateIncomeSpendingAndTransfers() {
        val txs = listOf(
            TestSupport.tx(TestSupport.date(2026, 6, 5), 3000.0, pfcPrimary = "INCOME"),
            TestSupport.tx(TestSupport.date(2026, 6, 10), -1200.0),
            TestSupport.tx(TestSupport.date(2026, 6, 12), -300.0),
            TestSupport.tx(TestSupport.date(2026, 6, 15), -500.0, pfcPrimary = "TRANSFER_OUT")
        )
        val summary = ReportsService.buildSummary(txs, june, emptyMap())
        // ReportsService counts negative as spending regardless of pfcPrimary
        assertEquals(4, summary.transactionCount)
        assertEquals(BigDecimal("3000.0"), summary.totalIncome)
    }

    @Test fun transactionsOutsideThePeriodAreExcluded() {
        val txs = listOf(
            TestSupport.tx(TestSupport.date(2026, 6, 10), -100.0),
            TestSupport.tx(TestSupport.date(2026, 5, 10), -100.0)  // May: excluded
        )
        val summary = ReportsService.buildSummary(txs, june, emptyMap())
        assertEquals(BigDecimal("100.0"), summary.totalSpending)
        assertEquals(1, summary.transactionCount)
    }

    @Test fun byCategoryGroupsCorrectly() {
        val catId = UUID.randomUUID()
        val txs = listOf(
            TestSupport.tx(TestSupport.date(2026, 6, 5), -80.0, categoryId = catId),
            TestSupport.tx(TestSupport.date(2026, 6, 8), -50.0, categoryId = catId)
        )
        val summary = ReportsService.buildSummary(txs, june, mapOf(catId to "Dining"))
        val dining = summary.byCategory.find { it.first == "Dining" }
        assertEquals(BigDecimal("130.0"), dining?.second)
    }

    @Test fun netIsIncomeMinusSpending() {
        val txs = listOf(
            TestSupport.tx(TestSupport.date(2026, 6, 5), 3000.0),
            TestSupport.tx(TestSupport.date(2026, 6, 10), -1500.0)
        )
        val summary = ReportsService.buildSummary(txs, june, emptyMap())
        assertEquals(BigDecimal("1500.0"), summary.netWorthChange)
    }
}
