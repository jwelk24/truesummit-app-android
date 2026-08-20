package com.truesummit.android.service

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.widget.TrueSummitSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Calendar
import java.util.Date

/**
 * Serializes the shared [TrueSummitSnapshot] to the paired Wear OS device.
 *
 * The snapshot itself is built by the same code the home-screen widgets use,
 * so the watch, the widgets, and the in-app Budget screen all report the same
 * numbers.
 */
class WearSyncService(private val context: Context) {

    suspend fun pushSnapshot() {
        val snapshot = TrueSummitSnapshot.build(context)

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        val billsJson = JSONArray()
        snapshot.upcomingBills.take(3).forEach { bill ->
            val daysUntil = ((bill.date.time - today.time) / (1000L * 60 * 60 * 24)).toInt()
            billsJson.put(JSONObject().apply {
                put("id", bill.id.toString())
                put("name", bill.name)
                put("amount", bill.amount.toDouble())
                put("daysUntil", daysUntil.coerceAtLeast(0))
            })
        }

        val db = AppDatabase.getInstance(context.applicationContext)

        val accounts = db.accountDao().getAll().first()
        val transactions = db.transactionDao().getAll().first()
        val scheduled = db.scheduledItemDao().getAll().first()
        val safe = SafeToSpendService.compute(
            accounts, scheduled, transactions, BigDecimal("500"), Date()
        )
        val health = FinancialHealthService.compute(transactions, accounts, Date(), context)

        val payload = JSONObject().apply {
            put("lastUpdated", snapshot.lastUpdated.time)
            put("currencyCode", snapshot.currencyCode)
            put("totalAssets", snapshot.totalAssets.toDouble())
            put("totalLiabilities", snapshot.totalLiabilities.toDouble())
            put("monthLabel", snapshot.monthLabel)
            put("budgetAssigned", snapshot.budgetAssigned.toDouble())
            put("budgetSpent", snapshot.budgetSpent.toDouble())
            put("upcomingBills", billsJson)
            if (safe.hasSpendableAccount) {
                put("safeToSpendToday", safe.safeToday.toDouble())
                put("safePerDay", safe.perDay.toDouble())
            } else {
                put("safeToSpendToday", JSONObject.NULL)
                put("safePerDay", JSONObject.NULL)
            }
            if (health.hasData) {
                put("healthScore", health.total)
                put("healthGrade", health.grade)
            } else {
                put("healthScore", JSONObject.NULL)
                put("healthGrade", JSONObject.NULL)
            }
        }

        val request = PutDataMapRequest.create("/truesummit/snapshot").apply {
            dataMap.putString("snapshot_json", payload.toString())
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request).await()
    }
}
