package com.truesummit.wear.complication

import com.truesummit.wear.WatchSnapshot

/** Total net worth — assets minus liabilities. */
class NetWorthComplicationService : SnapshotComplicationService() {

    override val label = "Worth"

    override val previewValue = "$24k"

    override fun value(snapshot: WatchSnapshot): String = compact(snapshot.netWorth)

    override fun longValue(snapshot: WatchSnapshot): String =
        "Net worth ${currency(snapshot.netWorth)}"

    /** SHORT_TEXT gets ~7 characters, so large balances are abbreviated. */
    private fun compact(amount: Double): String {
        val abs = kotlin.math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            abs >= 1_000_000 -> "$sign$%.1fM".format(abs / 1_000_000)
            abs >= 10_000 -> "$sign$%.0fk".format(abs / 1_000)
            abs >= 1_000 -> "$sign$%.1fk".format(abs / 1_000)
            else -> currency(amount)
        }
    }
}
