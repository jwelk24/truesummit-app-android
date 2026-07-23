package com.truesummit.android.service

import com.truesummit.android.data.entity.AccountEntity
import com.truesummit.android.data.entity.ScheduledItemEntity
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.data.model.AccountType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

data class SafeToSpend(
    val safeToday: BigDecimal,
    val perDay: BigDecimal,
    val spentToday: BigDecimal,
    val totalUntilIncome: BigDecimal,
    val nextIncomeDate: Date?,
    val daysUntilIncome: Int,
    val cushion: BigDecimal,
    val hasSpendableAccount: Boolean
) {
    val isTight: Boolean get() = totalUntilIncome <= BigDecimal.ZERO
}

object SafeToSpendService {

    /**
     * Projects checking + savings forward with scheduled items, finds the lowest
     * balance before the next income, and spreads headroom above the cushion
     * across remaining days.
     */
    fun compute(
        accounts: List<AccountEntity>,
        scheduled: List<ScheduledItemEntity>,
        transactions: List<TransactionEntity>,
        cushion: BigDecimal,
        now: Date = Date(),
        horizonDays: Int = 45
    ): SafeToSpend {
        val cal = Calendar.getInstance()
        cal.time = now
        val today = cal.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        val hasSpendable = accounts.any { it.type == AccountType.CHECKING || it.type == AccountType.SAVINGS }

        val spentToday = transactions
            .filter { it.date >= today && it.amount < BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.negate()) }

        if (!hasSpendable) {
            return SafeToSpend(BigDecimal.ZERO, BigDecimal.ZERO, spentToday, BigDecimal.ZERO, null, 0, cushion, false)
        }

        val startBalance = CashFlowForecasterUtils.spendableBalance(accounts)
        val projection = CashFlowForecaster(startBalance, scheduled, horizonDays).project()

        // Next income event
        val nextIncome = projection.events
            .filter { it.date > today && it.amount > BigDecimal.ZERO }
            .minByOrNull { it.date }?.date

        cal.time = today
        cal.add(Calendar.DAY_OF_YEAR, 14)
        val fallbackEnd = cal.time
        cal.time = today
        cal.add(Calendar.DAY_OF_YEAR, horizonDays)
        val horizonEnd = cal.time
        val windowEnd = minOf(nextIncome ?: fallbackEnd, horizonEnd)

        cal.time = today
        val todayMs = today.time
        val windowMs = windowEnd.time
        val days = maxOf(1, ((windowMs - todayMs) / (1000L * 60 * 60 * 24)).toInt())

        val lowest = projection.points
            .filter { it.date >= today && it.date <= windowEnd }
            .minOfOrNull { it.balance } ?: startBalance

        val totalUntilIncome = lowest.subtract(cushion)
        val perDay = if (days > 0) totalUntilIncome.divide(BigDecimal(days), 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
        val safeToday = perDay.subtract(spentToday)

        return SafeToSpend(
            safeToday = safeToday,
            perDay = perDay,
            spentToday = spentToday,
            totalUntilIncome = totalUntilIncome,
            nextIncomeDate = nextIncome,
            daysUntilIncome = days,
            cushion = cushion,
            hasSpendableAccount = true
        )
    }
}
