package com.truesummit.android.service

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.data.model.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

class WearSyncService(private val context: Context, private val db: AppDatabase) {

    suspend fun pushSnapshot() {
        val accounts = db.accountDao().getAll().first()
        val scheduled = db.scheduledItemDao().getAll().first()
        val transactions = db.transactionDao().getAll().first()

        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1

        val totalAssets = accounts
            .filter { it.balance > BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { a, acc -> a + acc.balance }
        val totalLiabilities = accounts
            .filter { it.balance < BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { a, acc -> a + acc.balance.abs() }

        val monthStart = Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val monthEnd = Date()

        val monthTx = transactions.filter { it.date in monthStart..monthEnd }
        val budgetMonth = db.budgetDao().getMonth(year, month)
        val budgetAssigned = budgetMonth?.let {
            db.budgetDao().getAllocationsForMonth(it.id).first()
                .fold(BigDecimal.ZERO) { a, alloc -> a + alloc.amount }
        } ?: BigDecimal.ZERO
        val budgetSpent = monthTx
            .filter { it.amount < BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { a, tx -> a + tx.amount.abs() }

        val safeToSpend = SafeToSpendService.compute(accounts, scheduled, transactions, BigDecimal("500"), Date())

        // Upcoming bills in next 14 days
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val horizon = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 14) }.time

        val billsJson = JSONArray()
        scheduled
            .filter { it.nextDate >= today && it.nextDate <= horizon }
            .sortedBy { it.nextDate }
            .take(3)
            .forEach { item ->
                val daysUntil = ((item.nextDate.time - today.time) / (1000L * 60 * 60 * 24)).toInt()
                billsJson.put(JSONObject().apply {
                    put("id", item.id.toString())
                    put("name", item.name)
                    put("amount", item.amount.toDouble())
                    put("daysUntil", daysUntil)
                })
            }

        val monthLabel = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())

        val snapshot = JSONObject().apply {
            put("lastUpdated", System.currentTimeMillis())
            put("currencyCode", "USD")
            put("totalAssets", totalAssets.toDouble())
            put("totalLiabilities", totalLiabilities.toDouble())
            put("monthLabel", monthLabel)
            put("budgetAssigned", budgetAssigned.toDouble())
            put("budgetSpent", budgetSpent.toDouble())
            put("upcomingBills", billsJson)
            put("safeToSpendToday", safeToSpend.safeToday.toDouble())
            put("safePerDay", safeToSpend.perDay.toDouble())
            val health = FinancialHealthService.compute(transactions, accounts, Date(), context)
            if (health.hasData) {
                put("healthScore", health.total)
                put("healthGrade", health.grade)
            } else {
                put("healthScore", JSONObject.NULL)
                put("healthGrade", JSONObject.NULL)
            }
        }

        val request = PutDataMapRequest.create("/truesummit/snapshot").apply {
            dataMap.putString("snapshot_json", snapshot.toString())
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request).await()
    }
}
