package com.truesummit.android.service

import java.math.BigDecimal
import java.util.*

enum class WhatIfKind { ONE_TIME, MONTHLY }

data class WhatIfChange(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val kind: WhatIfKind,
    /** Signed: negative = money out, positive = money in. */
    val amount: BigDecimal,
    /** ONE_TIME: when it happens. MONTHLY: when it starts. */
    val startDate: Date,
    /** MONTHLY only — null means ongoing for entire projection. */
    val durationMonths: Int? = null
)

data class WhatIfPoint(
    val date: Date,
    val baseline: BigDecimal,
    val scenario: BigDecimal
) {
    val diff: BigDecimal get() = scenario.subtract(baseline)
}

data class WhatIfProjection(val points: List<WhatIfPoint>) {
    fun at(monthsOut: Int): WhatIfPoint? =
        if (monthsOut in points.indices) points[monthsOut] else points.lastOrNull()
}

object WhatIfService {

    /**
     * Projects net worth month-by-month: baseline = current + monthlyDelta×m;
     * scenario layers every WhatIfChange on top of the baseline.
     */
    fun projectNetWorth(
        currentNetWorth: BigDecimal,
        baselineMonthly: BigDecimal,
        changes: List<WhatIfChange>,
        months: Int,
        now: Date = Date()
    ): WhatIfProjection {
        val cal = Calendar.getInstance()
        cal.time = startOfDay(now)
        val start = cal.time

        val points = mutableListOf<WhatIfPoint>()
        for (m in 0..months) {
            cal.time = start; cal.add(Calendar.MONTH, m)
            val date = cal.time
            val baseline = currentNetWorth.add(baselineMonthly.multiply(BigDecimal(m)))
            val scenarioDelta = changes.fold(BigDecimal.ZERO) { acc, change ->
                acc.add(cumulativeEffect(change, date, start))
            }
            points.add(WhatIfPoint(date, baseline, baseline.add(scenarioDelta)))
        }
        return WhatIfProjection(points)
    }

    private fun cumulativeEffect(change: WhatIfChange, date: Date, start: Date): BigDecimal {
        return when (change.kind) {
            WhatIfKind.ONE_TIME -> {
                if (change.startDate <= date) change.amount else BigDecimal.ZERO
            }
            WhatIfKind.MONTHLY -> {
                val effectiveStart = maxOf(change.startDate, start)
                if (effectiveStart > date) return BigDecimal.ZERO
                val cal = Calendar.getInstance()
                cal.time = effectiveStart
                val startMs = cal.timeInMillis
                cal.time = date
                // rough month count via date component
                val months = monthsBetween(effectiveStart, date) + 1
                val active = minOf(months, change.durationMonths ?: Int.MAX_VALUE)
                change.amount.multiply(BigDecimal(maxOf(active, 0)))
            }
        }
    }

    private fun monthsBetween(from: Date, to: Date): Int {
        val cal = Calendar.getInstance()
        cal.time = from
        val y1 = cal.get(Calendar.YEAR); val m1 = cal.get(Calendar.MONTH)
        cal.time = to
        val y2 = cal.get(Calendar.YEAR); val m2 = cal.get(Calendar.MONTH)
        return (y2 - y1) * 12 + (m2 - m1)
    }

    private fun startOfDay(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }
}
