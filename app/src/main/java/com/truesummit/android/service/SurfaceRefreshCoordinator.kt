package com.truesummit.android.service

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.widget.BudgetWidget
import com.truesummit.android.widget.NetWorthWidget
import com.truesummit.android.widget.UpcomingBillsWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the home-screen widgets and the paired watch in step with the database.
 *
 * Room emits on every write, so instead of hooking each insert/update/delete
 * site individually we observe the tables the off-app surfaces actually read
 * and refresh on change. Emissions are debounced so a burst of writes (a CSV
 * import, a bulk categorize) results in one refresh rather than hundreds.
 */
object SurfaceRefreshCoordinator {

    private const val TAG = "SurfaceRefresh"

    /** Long enough to coalesce a bulk import, short enough to feel immediate. */
    private const val DEBOUNCE_MS = 1_500L

    private var started = false

    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        if (started) return
        started = true

        val app = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            val db = Room.databaseBuilder(app, AppDatabase::class.java, "truesummit-db")
                .addMigrations(
                    AppDatabase.MIGRATION_1_2,
                    AppDatabase.MIGRATION_2_3,
                    AppDatabase.MIGRATION_3_4
                )
                .build()

            // Any of these changing can move a number on a widget or the watch.
            combine(
                db.transactionDao().getAll(),
                db.accountDao().getAll(),
                db.scheduledItemDao().getAll(),
                db.categoryDao().getCategories()
            ) { _, _, _, _ -> Unit }
                .debounce(DEBOUNCE_MS)
                // Drop in-flight work if another change lands mid-refresh.
                .collectLatest { refresh(app) }
        }
    }

    /** Rebuilds every off-app surface. Failures are logged, never fatal. */
    private suspend fun refresh(context: Context) {
        runCatching {
            BudgetWidget().updateAll(context)
            NetWorthWidget().updateAll(context)
            UpcomingBillsWidget().updateAll(context)
        }.onFailure { Log.w(TAG, "Widget refresh failed", it) }

        runCatching {
            WearSyncService(context).pushSnapshot()
        }.onFailure {
            // Expected when no watch is paired — the Data Layer call rejects.
            Log.d(TAG, "Watch sync skipped: ${it.message}")
        }
    }
}
