package com.truesummit.wear.complication

import com.truesummit.wear.WatchSnapshot

/** Mirrors the iOS "Safe to Spend" complication. */
class SafeToSpendComplicationService : SnapshotComplicationService() {

    override val label = "Safe"

    override val previewValue = "$142"

    override fun value(snapshot: WatchSnapshot): String =
        snapshot.safeToSpendToday?.let { currency(it) } ?: "—"

    override fun longValue(snapshot: WatchSnapshot): String {
        val today = snapshot.safeToSpendToday?.let { currency(it) } ?: "—"
        val perDay = snapshot.safePerDay?.let { " · ${currency(it)}/day" } ?: ""
        return "$today$perDay"
    }
}
