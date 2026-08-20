package com.truesummit.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.truesummit.android.R
import com.truesummit.android.data.AppDatabase
import com.truesummit.android.ui.transactions.formatCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

object SpendingTodayManager {
    private const val CHANNEL_ID = "spending_today"
    private const val NOTIFICATION_ID = 1001
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startOrUpdate(context: Context) {
        scope.launch {
            val stats = compute(context)
            showNotification(context, stats)
        }
    }

    private suspend fun compute(context: Context): DayStats {
        val db = AppDatabase.getInstance(context)
        val calendar = Calendar.getInstance()
        val today = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val tomorrow = calendar.apply { add(Calendar.DAY_OF_YEAR, 1) }.time

        val transactions = db.transactionDao().getAll().first().filter {
            it.date.after(today) && it.date.before(tomorrow) && it.amount < BigDecimal.ZERO
        }

        val spent = transactions.fold(BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount.abs()) }
        val topMerchant = transactions.maxByOrNull { it.amount.abs() }?.merchant

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val budgetMonth = db.budgetDao().getMonth(year, month)
        
        val allocations = budgetMonth?.let { db.budgetDao().getAllocationsForMonth(it.id).first() } ?: emptyList()
        val totalAssigned = allocations.fold(BigDecimal.ZERO) { acc, alloc -> acc.add(alloc.amount) }
        
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dailyBudget = if (daysInMonth > 0) totalAssigned.divide(BigDecimal(daysInMonth), 2, RoundingMode.HALF_UP) else BigDecimal.ZERO

        val account = db.accountDao().getAll().first().firstOrNull()
        val currencyCode = account?.currencyCode ?: "USD"

        return DayStats(
            spent = spent,
            count = transactions.size,
            topMerchant = topMerchant,
            dailyBudget = dailyBudget,
            currencyCode = currencyCode
        )
    }

    private fun showNotification(context: Context, stats: DayStats) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Spending Today", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows today's spending progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val remoteViews = RemoteViews(context.packageName, R.layout.notification_spending_today).apply {
            setTextViewText(R.id.tv_spent_today, formatCurrency(stats.spent.toDouble()))
            setTextViewText(R.id.tv_daily_budget, formatCurrency(stats.dailyBudget.toDouble()))
            setTextViewText(R.id.tv_transaction_count, "${stats.count} transactions")
            setTextViewText(R.id.tv_top_merchant, stats.topMerchant?.let { "Last: $it" } ?: "")
            
            val progress = if (stats.dailyBudget > BigDecimal.ZERO) {
                stats.spent.divide(stats.dailyBudget, 2, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toInt()
            } else 0
            setProgressBar(R.id.pb_spending, 100, progress.coerceAtMost(100), false)
            
            val color = when {
                progress > 100 -> 0xFFEF4444.toInt() // Red
                progress > 85 -> 0xFFF59E0B.toInt()  // Orange
                else -> 0xFF10B981.toInt()           // Green
            }
            setTextColor(R.id.tv_spent_today, color)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun endAll(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private data class DayStats(
        val spent: BigDecimal,
        val count: Int,
        val topMerchant: String?,
        val dailyBudget: BigDecimal,
        val currencyCode: String
    )
}
