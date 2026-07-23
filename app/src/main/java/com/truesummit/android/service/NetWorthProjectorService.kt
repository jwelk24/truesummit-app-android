package com.truesummit.android.service

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import kotlin.math.abs
import kotlin.math.floor

data class NetWorthMilestone(
    val current: BigDecimal,
    val target: BigDecimal,
    val monthlyChange: BigDecimal,
    val etaDate: Date?
) {
    val progress: Double
        get() {
            if (target <= BigDecimal.ZERO) return 0.0
            return minOf(1.0, maxOf(0.0, current.toDouble() / target.toDouble()))
        }
}

object NetWorthProjectorService {

    /** Next round milestone above `current`, with step scaled to magnitude. */
    fun nextMilestone(above: BigDecimal): BigDecimal {
        val value = above.toDouble()
        val step = when (abs(value)) {
            in 0.0..<1_000.0 -> 500.0
            in 1_000.0..<10_000.0 -> 1_000.0
            in 10_000.0..<100_000.0 -> 10_000.0
            in 100_000.0..<1_000_000.0 -> 50_000.0
            else -> 250_000.0
        }
        return BigDecimal.valueOf((floor(value / step) + 1) * step)
    }

    fun project(current: BigDecimal, monthlyChange: BigDecimal, now: Date = Date()): NetWorthMilestone {
        val target = nextMilestone(current)
        var eta: Date? = null
        val monthly = monthlyChange.toDouble()
        if (monthly > 0.01) {
            val remaining = target.subtract(current).toDouble()
            val months = Math.ceil(remaining / monthly).toInt()
            if (months in 1..1200) {
                val cal = Calendar.getInstance()
                cal.time = now
                cal.add(Calendar.MONTH, months)
                eta = cal.time
            }
        }
        return NetWorthMilestone(current, target, monthlyChange, eta)
    }
}
