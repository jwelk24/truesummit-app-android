package com.truesummit.android.ui.subscriptions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.entity.ScheduledItemEntity
import com.truesummit.android.data.model.ScheduledKind
import com.truesummit.android.service.DetectedSubscription
import com.truesummit.android.service.SubscriptionTracker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class SubscriptionsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "truesummit-db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()

    private val _detected = MutableStateFlow<List<DetectedSubscription>>(emptyList())
    val detected: StateFlow<List<DetectedSubscription>> = _detected

    private val _addedNotice = MutableStateFlow<String?>(null)
    val addedNotice: StateFlow<String?> = _addedNotice

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    fun rescan() {
        viewModelScope.launch {
            _isScanning.value = true
            val transactions = db.transactionDao().getAll().first()
            _detected.value = SubscriptionTracker.detect(transactions, context = getApplication())
            _isScanning.value = false
            _addedNotice.value = null
        }
    }

    fun ignore(sub: DetectedSubscription) {
        SubscriptionTracker.ignore(getApplication(), sub.merchant)
        rescan()
    }

    fun schedule(sub: DetectedSubscription) {
        viewModelScope.launch {
            val firstTx = sub.occurrences.firstOrNull()
            val item = ScheduledItemEntity(
                kind = ScheduledKind.SUBSCRIPTION,
                name = sub.merchant,
                amount = sub.typicalAmount.negate(),
                nextDate = sub.predictedNextDate,
                intervalDays = sub.cadence.intervalDays,
                accountId = firstTx?.accountId,
                categoryId = firstTx?.categoryId
            )
            db.scheduledItemDao().insert(item)
            _addedNotice.value = "Added ${sub.merchant} to Horizon."
        }
    }

    fun clearNotice() {
        _addedNotice.value = null
    }

    fun getIgnoredMerchants(): List<String> {
        return SubscriptionTracker.getIgnoredMerchants(getApplication()).toList()
    }

    fun restore(merchant: String) {
        SubscriptionTracker.restore(getApplication(), merchant)
        rescan()
    }
}
