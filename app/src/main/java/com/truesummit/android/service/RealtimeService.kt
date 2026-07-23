package com.truesummit.android.service

import android.content.Context
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.*

object RealtimeService {
    private var channel: RealtimeChannel? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null
    private var applicationContext: Context? = null
    private var currentHouseholdID: UUID? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val subscribedTables = listOf(
        "accounts", "category_groups", "categories",
        "goals", "budget_months", "budget_allocations",
        "scheduled_items", "transactions", "transaction_splits",
        "balance_snapshots"
    )

    fun start(context: Context, householdID: UUID) {
        if (currentHouseholdID == householdID && _isConnected.value) return
        stop()
        this.applicationContext = context.applicationContext
        this.currentHouseholdID = householdID
        job = scope.launch {
            try {
                val ch = SupabaseService.client.channel("truesummit-household-${householdID.toString().lowercase()}")
                val filter = "household_id=eq.${householdID.toString().lowercase()}"
                for (table in subscribedTables) {
                    ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                        this.table = table
                        this.filter = filter
                    }.onEach { handleEvent() }.launchIn(this)
                    ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                        this.table = table
                        this.filter = filter
                    }.onEach { handleEvent() }.launchIn(this)
                    ch.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                        this.table = table
                        this.filter = filter
                    }.onEach { handleEvent() }.launchIn(this)
                }
                ch.subscribe()
                channel = ch
                _isConnected.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                _isConnected.value = false
            }
        }
    }

    private var debounceJob: Job? = null
    private fun handleEvent() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(1000)
            applicationContext?.let {
                SyncService.syncAll(it)
            }
        }
    }

    fun stop() {
        job?.cancel()
        debounceJob?.cancel()
        scope.launch {
            try { channel?.unsubscribe() } catch (e: Exception) { /* ignore */ }
            channel = null
            currentHouseholdID = null
            _isConnected.value = false
        }
    }
}
