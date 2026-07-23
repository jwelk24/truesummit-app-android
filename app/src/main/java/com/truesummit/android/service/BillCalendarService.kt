package com.truesummit.android.service

import com.truesummit.android.data.entity.ScheduledItemEntity
import com.truesummit.android.data.model.ScheduledKind
import java.math.BigDecimal
import java.util.*

data class BillOccurrence(
    val item: ScheduledItemEntity,
    val date: Date
) {
    val isIncome: Boolean get() = item.kind == ScheduledKind.PAYCHECK || item.amount > BigDecimal.ZERO
}

object BillCalendarService {

    /**
     * Expands recurring scheduled items into concrete occurrences for a given
     * month, mirroring iOS BillCalendarEngine.occurrencesByDay.
     */
    fun occurrencesByDay(
        scheduled: List<ScheduledItemEntity>,
        monthAnchor: Date
    ): Map<Date, List<BillOccurrence>> {
        val cal = Calendar.getInstance()
        cal.time = monthAnchor
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.time

        cal.add(Calendar.MONTH, 1)
        val monthEnd = cal.time

        val result = mutableMapOf<Date, MutableList<BillOccurrence>>()

        for (item in scheduled) {
            var date = item.nextDate

            if (item.intervalDays <= 0) {
                // one-shot: include only if it lands in this month
                if (date >= monthStart && date < monthEnd) {
                    val day = startOfDay(date)
                    result.getOrPut(day) { mutableListOf() }.add(BillOccurrence(item, day))
                }
                continue
            }

            var safety = 0
            // Roll forward to reach the month
            while (date < monthStart && safety < 400) {
                date = addDays(date, item.intervalDays)
                safety++
            }
            // Emit occurrences within the month
            while (date < monthEnd && safety < 400) {
                if (date >= monthStart) {
                    val day = startOfDay(date)
                    result.getOrPut(day) { mutableListOf() }.add(BillOccurrence(item, day))
                }
                date = addDays(date, item.intervalDays)
                safety++
            }
        }
        return result
    }

    private fun startOfDay(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun addDays(date: Date, days: Int): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, days)
        return cal.time
    }
}
