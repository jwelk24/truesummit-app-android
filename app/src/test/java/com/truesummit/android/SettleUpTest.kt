package com.truesummit.android

import com.truesummit.android.ui.settleup.SettleUpStore
import com.truesummit.android.ui.settleup.SharedExpense
import com.truesummit.android.ui.settleup.SettlementRecord
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.*

private fun assertBD(expected: Double, actual: BigDecimal) {
    assertTrue("expected $expected but was $actual",
        BigDecimal(expected.toString()).compareTo(actual) == 0)
}

class SettleUpTest {

    private fun expense(paidByMe: Boolean, amount: Double, payerShare: Double) = SharedExpense(
        title = "Test",
        amount = BigDecimal(amount.toString()),
        date = Date(),
        paidByMe = paidByMe,
        payerShare = BigDecimal(payerShare.toString()),
        note = null
    )

    @Test fun payingFiftyFiftyExpenseOwedHalf() {
        val e = expense(paidByMe = true, amount = 100.0, payerShare = 50.0)
        val balance = SettleUpStore.netBalance(listOf(e), emptyList())
        assertBD(50.0, balance)
    }

    @Test fun partnerExpenseMeansIOweMyShare() {
        val e = expense(paidByMe = false, amount = 100.0, payerShare = 50.0)
        val balance = SettleUpStore.netBalance(listOf(e), emptyList())
        assertBD(-50.0, balance)
    }

    @Test fun unevenSplitUsesPayerShare() {
        // I paid $90, my share only $30 → household owes me $60
        val e = expense(paidByMe = true, amount = 90.0, payerShare = 30.0)
        val balance = SettleUpStore.netBalance(listOf(e), emptyList())
        assertBD(60.0, balance)
    }

    @Test fun settlementZeroesBalance() {
        val e = expense(paidByMe = false, amount = 100.0, payerShare = 50.0)
        // I owe -50; I pay 50 (fromMe=true) → 0
        val s = SettlementRecord(fromMe = true, amount = BigDecimal("50.0"), date = Date())
        val balance = SettleUpStore.netBalance(listOf(e), listOf(s))
        assertBD(0.0, balance)
    }

    @Test fun receivingSettlementClearsWhatIWasOwed() {
        val e = expense(paidByMe = true, amount = 100.0, payerShare = 50.0)
        // Partner owes me 50; partner pays (fromMe=false) → 0
        val s = SettlementRecord(fromMe = false, amount = BigDecimal("50.0"), date = Date())
        val balance = SettleUpStore.netBalance(listOf(e), listOf(s))
        assertBD(0.0, balance)
    }

    @Test fun mixedHistoryNetsOut() {
        val expenses = listOf(
            expense(paidByMe = true, amount = 200.0, payerShare = 100.0),  // +100
            expense(paidByMe = false, amount = 60.0, payerShare = 30.0)    // -30
        )
        val balance = SettleUpStore.netBalance(expenses, emptyList())
        assertBD(70.0, balance)
    }
}
