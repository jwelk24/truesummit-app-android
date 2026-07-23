package com.truesummit.android.service

import com.truesummit.android.data.entity.AccountEntity
import com.truesummit.android.data.entity.ScheduledItemEntity
import com.truesummit.android.data.model.AccountType
import com.truesummit.android.data.model.ScheduledKind
import java.math.BigDecimal
import java.util.*

data class ForecastPoint(
    val id: UUID = UUID.randomUUID(),
    val date: Date,
    val balance: BigDecimal,
    val dailyDelta: BigDecimal,
    val events: List<ForecastEvent>
)

data class ForecastEvent(
    val id: UUID = UUID.randomUUID(),
    val date: Date,
    val name: String,
    val kind: ScheduledKind,
    val amount: BigDecimal,
    val itemId: UUID
)

data class ForecastResult(
    val points: List<ForecastPoint>,
    val events: List<ForecastEvent>,
    val startingBalance: BigDecimal,
    val endingBalance: BigDecimal,
    val lowest: ForecastPoint?
) {
    fun getBalance(daysOut: Int): BigDecimal? {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, daysOut)
        val target = calendar.time
        
        return points.find { 
            val pCal = Calendar.getInstance().apply { time = it.date }
            val tCal = Calendar.getInstance().apply { time = target }
            pCal.get(Calendar.YEAR) == tCal.get(Calendar.YEAR) &&
            pCal.get(Calendar.DAY_OF_YEAR) == tCal.get(Calendar.DAY_OF_YEAR)
        }?.balance
    }
}

object CashFlowForecasterUtils {
    fun spendableBalance(accounts: List<AccountEntity>): BigDecimal =
        accounts
            .filter { it.type == AccountType.CHECKING || it.type == AccountType.SAVINGS }
            .fold(BigDecimal.ZERO) { acc, a -> acc.add(a.balance) }
}

class CashFlowForecaster(
    private val startingBalance: BigDecimal,
    private val scheduled: List<ScheduledItemEntity>,
    private val horizonDays: Int
) {
    fun project(
        excludedItemIDs: Set<UUID> = emptySet(),
        extraEvents: List<ForecastEvent> = emptyList()
    ): ForecastResult {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val today = calendar.time
        
        calendar.add(Calendar.DAY_OF_YEAR, horizonDays)
        val horizon = calendar.time
        
        val allEvents = mutableListOf<ForecastEvent>()
        allEvents.addAll(extraEvents.filter { it.date >= today && !it.date.after(horizon) })
        
        for (item in scheduled) {
            if (excludedItemIDs.contains(item.id)) continue
            
            var date = item.nextDate
            var safety = 0
            while (!date.after(horizon) && safety < 365) {
                if (!date.before(today)) {
                    allEvents.add(ForecastEvent(
                        date = truncateTime(date),
                        name = item.name,
                        kind = item.kind,
                        amount = item.amount,
                        itemId = item.id
                    ))
                }
                if (item.intervalDays <= 0) break
                val cal = Calendar.getInstance()
                cal.time = date
                cal.add(Calendar.DAY_OF_YEAR, item.intervalDays)
                date = cal.time
                safety++
            }
        }
        allEvents.sortBy { it.date }
        
        val points = mutableListOf<ForecastPoint>()
        var running = startingBalance
        var lowest: ForecastPoint? = null
        
        val cursor = Calendar.getInstance()
        cursor.time = today
        
        var eventIdx = 0
        while (!cursor.time.after(horizon)) {
            var dailyDelta = BigDecimal.ZERO
            val dayEvents = mutableListOf<ForecastEvent>()
            
            while (eventIdx < allEvents.size && isSameDay(allEvents[eventIdx].date, cursor.time)) {
                dailyDelta = dailyDelta.add(allEvents[eventIdx].amount)
                dayEvents.add(allEvents[eventIdx])
                eventIdx++
            }
            
            running = running.add(dailyDelta)
            val point = ForecastPoint(date = cursor.time, balance = running, dailyDelta = dailyDelta, events = dayEvents)
            points.add(point)
            
            if (lowest == null || running < lowest.balance) {
                lowest = point
            }
            
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        return ForecastResult(
            points = points,
            events = allEvents,
            startingBalance = startingBalance,
            endingBalance = running,
            lowest = lowest
        )
    }
    
    private fun truncateTime(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }
    
    private fun isSameDay(d1: Date, d2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = d1 }
        val cal2 = Calendar.getInstance().apply { time = d2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
