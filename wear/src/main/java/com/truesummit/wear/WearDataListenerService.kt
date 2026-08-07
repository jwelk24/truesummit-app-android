package com.truesummit.wear

import android.content.ComponentName
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.truesummit.wear.complication.BudgetComplicationService
import com.truesummit.wear.complication.NetWorthComplicationService
import com.truesummit.wear.complication.SafeToSpendComplicationService
import com.truesummit.wear.tile.TrueSummitTileService

class WearDataListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        var changed = false
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/truesummit/snapshot"
            ) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val json = map.getString("snapshot_json") ?: continue
                SnapshotRepository.save(this, json)
                changed = true
            }
        }
        if (changed) refreshSurfaces()
    }

    /** Tells the watch face and tile to redraw with the new numbers. */
    private fun refreshSurfaces() {
        listOf(
            SafeToSpendComplicationService::class.java,
            BudgetComplicationService::class.java,
            NetWorthComplicationService::class.java
        ).forEach { cls ->
            runCatching {
                ComplicationDataSourceUpdateRequester
                    .create(this, ComponentName(this, cls))
                    .requestUpdateAll()
            }
        }
        runCatching {
            TileService.getUpdater(this).requestUpdate(TrueSummitTileService::class.java)
        }
    }
}
