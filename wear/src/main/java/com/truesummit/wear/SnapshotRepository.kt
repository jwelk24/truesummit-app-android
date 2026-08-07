package com.truesummit.wear

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persists the latest phone snapshot to SharedPreferences.
 *
 * The watch app, the complication services, and the tile service are each
 * separate entry points that the system may start in a cold process, so an
 * in-memory value alone would read null. Everything reads through here.
 */
object SnapshotRepository {

    private const val PREFS = "truesummit_wear"
    private const val KEY_JSON = "snapshot_json"
    private const val KEY_UPDATED = "snapshot_updated_at"

    private val _snapshot = MutableStateFlow<WatchSnapshot?>(null)
    val snapshot: StateFlow<WatchSnapshot?> = _snapshot

    private var loaded = false

    /** Reads from disk once per process, then serves the cached value. */
    fun load(context: Context): WatchSnapshot? {
        if (!loaded) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_JSON, null)
            _snapshot.value = json?.let { WatchSnapshot.fromJson(it) }
            loaded = true
        }
        return _snapshot.value
    }

    /** Persists a newly received payload and publishes it to any live collectors. */
    fun save(context: Context, json: String) {
        val parsed = WatchSnapshot.fromJson(json) ?: return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, json)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
        loaded = true
        _snapshot.value = parsed
    }

    fun lastUpdated(context: Context): Long =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_UPDATED, 0L)
}
