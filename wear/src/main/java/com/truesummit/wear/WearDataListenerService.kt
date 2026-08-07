package com.truesummit.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow

class WearDataListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/truesummit/snapshot"
            ) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val json = map.getString("snapshot_json") ?: continue
                WatchSnapshotStore.update(WatchSnapshot.fromJson(json))
            }
        }
    }
}

object WatchSnapshotStore {
    val snapshot = MutableStateFlow<WatchSnapshot?>(null)
    fun update(s: WatchSnapshot?) { snapshot.value = s }
}
