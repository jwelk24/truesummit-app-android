package com.truesummit.wear.complication

import com.truesummit.wear.WatchSnapshot

/** Budget remaining, with the used fraction driving the ranged arc. */
class BudgetComplicationService : SnapshotComplicationService() {

    override val label = "Budget"

    override val previewValue = "$820"

    override fun value(snapshot: WatchSnapshot): String =
        currency(snapshot.budgetRemaining)

    override fun longValue(snapshot: WatchSnapshot): String =
        "${currency(snapshot.budgetRemaining)} left · ${snapshot.monthLabel}"

    override fun fraction(snapshot: WatchSnapshot): Float =
        snapshot.budgetUsedFraction.toFloat()
}
