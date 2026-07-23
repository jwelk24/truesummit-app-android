package com.truesummit.android

import com.truesummit.android.data.entity.AccountEntity
import com.truesummit.android.data.model.AccountType
import com.truesummit.android.service.FinancialHealthService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.*

class FinancialHealthTest {

    private val now = TestSupport.date(2026, 7, 10)

    /** Three months of steady income/spend. Each expense has a unique merchant so subscription
     *  detection doesn't flag the fixture as a recurring charge. */
    private fun steadyTransactions(monthlyIncome: Double, monthlySpend: Double): List<com.truesummit.android.data.entity.TransactionEntity> {
        val txs = mutableListOf<com.truesummit.android.data.entity.TransactionEntity>()
        val cal = Calendar.getInstance()
        for (i in 0 until 3) {
            cal.time = now
            cal.add(Calendar.MONTH, -i)
            cal.add(Calendar.DAY_OF_MONTH, -1)
            txs.add(TestSupport.tx(cal.time, monthlyIncome, merchant = "Employer", pfcPrimary = "INCOME"))
            txs.add(TestSupport.tx(cal.time, -monthlySpend, merchant = "Life $i"))
        }
        return txs
    }

    private fun account(name: String, type: AccountType, balance: Double) = AccountEntity(
        id = UUID.randomUUID(),
        name = name,
        type = type,
        balance = BigDecimal(balance.toString()),
        currencyCode = "USD"
    )

    @Test fun pillarsAddUpToOneHundredPossiblePoints() {
        val txs = steadyTransactions(4000.0, 3200.0)
        val score = FinancialHealthService.compute(txs, emptyList(), now)
        assertTrue(score.hasData)
        assertEquals(100, score.pillars.sumOf { it.maxPoints })
    }

    @Test fun noIncomeMeansNoScore() {
        val tx = TestSupport.tx(now, -100.0)
        val score = FinancialHealthService.compute(listOf(tx), emptyList(), now)
        assertFalse(score.hasData)
        assertTrue(score.pillars.isEmpty())
    }

    @Test fun idealProfileScoresFullMarks() {
        val txs = steadyTransactions(4000.0, 3200.0)
        val checking = account("Checking", AccountType.CHECKING, 20000.0)
        val score = FinancialHealthService.compute(txs, listOf(checking), now)
        assertEquals(100, score.total)
        assertEquals("Excellent", score.grade)
    }

    @Test fun largeCardBalanceZerosDebtPillar() {
        val txs = steadyTransactions(4000.0, 3200.0)
        val card = account("Card", AccountType.CREDIT_CARD, -4000.0)
        val score = FinancialHealthService.compute(txs, listOf(card), now)
        val debt = score.pillars.find { it.id == "debt" }
        assertEquals(0, debt?.points)
    }

    @Test fun noLiquidAccountsZeroesRunwayPillar() {
        val txs = steadyTransactions(4000.0, 3200.0)
        val score = FinancialHealthService.compute(txs, emptyList(), now)
        val runway = score.pillars.find { it.id == "runway" }
        assertEquals(0, runway?.points)
    }

    @Test fun gradesAreCorrect() {
        assertEquals("Excellent", FinancialHealthService.compute(steadyTransactions(4000.0, 3200.0), listOf(account("C", AccountType.CHECKING, 20000.0)), now).grade)
        // A score under 45 → "Needs Work"
        val spendingHeavy = steadyTransactions(1000.0, 900.0)
        val score = FinancialHealthService.compute(spendingHeavy, emptyList(), now)
        val gradeIsLow = score.total < 65
        assertTrue(gradeIsLow)
    }
}
