package com.truesummit.android.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.truesummit.android.R
import com.truesummit.android.data.entity.TransactionEntity
import com.truesummit.android.ui.inbox.ReviewQueue
import java.util.Calendar
import java.util.Date

object EngagementNudgesService {
    const val CHANNEL_ID = "truesummit_engagement"
    const val WEEKLY_NOTIFICATION_ID = 2001
    const val MONTHLY_NOTIFICATION_ID = 2002
    const val ACTION_WEEKLY = "com.truesummit.android.NUDGE_WEEKLY"
    const val ACTION_MONTHLY = "com.truesummit.android.NUDGE_MONTHLY"
    const val EXTRA_DESTINATION = "nudge_destination"
    const val DEST_REVIEW_INBOX = "reviewInbox"
    const val DEST_WEEKLY_REVIEW = "weeklyReview"
    const val DEST_MONTH_RECAP = "monthRecap"

    private const val KEY_WEEKLY_ENABLED = "nudges.weekly.enabled"
    private const val KEY_MONTHLY_ENABLED = "nudges.monthly.enabled"
    private const val PREFS = "truesummit_prefs"

    var weeklyNudgeEnabled: Boolean
        get() = prefs?.getBoolean(KEY_WEEKLY_ENABLED, true) ?: true
        set(value) { prefs?.edit()?.putBoolean(KEY_WEEKLY_ENABLED, value)?.apply() }

    var monthlySummaryEnabled: Boolean
        get() = prefs?.getBoolean(KEY_MONTHLY_ENABLED, true) ?: true
        set(value) { prefs?.edit()?.putBoolean(KEY_MONTHLY_ENABLED, value)?.apply() }

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        createNotificationChannel(context)
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Weekly & Monthly Check-ins",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Periodic reminders to review your finances."
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun refresh(context: Context, transactions: List<TransactionEntity>) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (weeklyNudgeEnabled) scheduleWeekly(context, transactions)
        if (monthlySummaryEnabled) scheduleMonthly(context)
    }

    private fun scheduleWeekly(context: Context, transactions: List<TransactionEntity>) {
        val cutoff = Date(System.currentTimeMillis() - 7 * 24 * 3600 * 1000L)
        val newCount = transactions.count { it.date >= cutoff }
        val reviewCount = ReviewQueue.pending(transactions).size

        val (title, body, dest) = weeklyContent(newCount, reviewCount)
        scheduleAlarm(context, ACTION_WEEKLY, nextSundayAt9am(), title, body, dest)
    }

    private fun scheduleMonthly(context: Context) {
        val (title, body, dest) = monthlyContent(Date())
        scheduleAlarm(context, ACTION_MONTHLY, nextFirstOfMonthAt9am(), title, body, dest)
    }

    fun weeklyContent(newCount: Int, reviewCount: Int): Triple<String, String, String> {
        if (reviewCount > 0) {
            val needs = if (reviewCount == 1) "needs a category" else "need a category"
            val body = if (newCount > 0)
                "$newCount new ${if (newCount == 1) "transaction" else "transactions"} this week — $reviewCount $needs."
            else
                "$reviewCount ${if (reviewCount == 1) "transaction" else "transactions"} still $needs."
            return Triple("Weekly check-in", body, DEST_REVIEW_INBOX)
        }
        if (newCount > 0) {
            return Triple(
                "Weekly check-in",
                "$newCount new ${if (newCount == 1) "transaction" else "transactions"} this week, all categorized. Take your three-minute review.",
                DEST_WEEKLY_REVIEW
            )
        }
        return Triple("Weekly check-in", "Time for your weekly money check-in — it takes about three minutes.", DEST_WEEKLY_REVIEW)
    }

    fun monthlyContent(date: Date): Triple<String, String, String> {
        val month = java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault()).format(date)
        return Triple(
            "Your $month summary is ready",
            "Income, spending, and the biggest category changes — see how $month stacked up.",
            DEST_MONTH_RECAP
        )
    }

    private fun scheduleAlarm(context: Context, action: String, fireAt: Long, title: String, body: String, dest: String) {
        val intent = Intent(context, EngagementNudgesReceiver::class.java).apply {
            this.action = action
            putExtra("title", title)
            putExtra("body", body)
            putExtra(EXTRA_DESTINATION, dest)
        }
        val pi = PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(AlarmManager::class.java)
        am?.set(AlarmManager.RTC_WAKEUP, fireAt, pi)
    }

    private fun nextSundayAt9am(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    private fun nextFirstOfMonthAt9am(): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        return cal.timeInMillis
    }
}

class EngagementNudgesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val body = intent.getStringExtra("body") ?: return
        val dest = intent.getStringExtra(EngagementNudgesService.EXTRA_DESTINATION) ?: ""

        val notificationId = when (intent.action) {
            EngagementNudgesService.ACTION_WEEKLY -> EngagementNudgesService.WEEKLY_NOTIFICATION_ID
            EngagementNudgesService.ACTION_MONTHLY -> EngagementNudgesService.MONTHLY_NOTIFICATION_ID
            else -> return
        }

        val tapIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EngagementNudgesService.EXTRA_DESTINATION, dest)
        }
        val tapPi = PendingIntent.getActivity(
            context, notificationId, tapIntent ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, EngagementNudgesService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(tapPi)
            .build()

        NotificationManagerCompat.from(context)
            .notify(notificationId, notification)
    }
}
