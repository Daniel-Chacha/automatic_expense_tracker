package com.personal.expensetracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.personal.expensetracker.R
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.util.AppConfig
import com.personal.expensetracker.util.FormatUtils
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Daily digest notification: shows today's spending summary.
 * Scheduled to run once per day in the evening.
 */
class DigestWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val cal = Calendar.getInstance()
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayEnd = System.currentTimeMillis()

        val todayTxns = db.transactionDao().getByDateRange(dayStart, dayEnd).first()
        val totalSpent = todayTxns.filter { it.type == com.personal.expensetracker.data.local.entity.TransactionType.EXPENSE }
            .sumOf { it.amount }
        val totalReceived = todayTxns.filter { it.type == com.personal.expensetracker.data.local.entity.TransactionType.INCOME }
            .sumOf { it.amount }
        val txnCount = todayTxns.size

        if (txnCount == 0) return Result.success()

        createChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Today's Summary")
            .setContentText("Spent ${FormatUtils.formatAmount(totalSpent)} • Received ${FormatUtils.formatAmount(totalReceived)} • $txnCount transactions")
            .setAutoCancel(true)
            .build()

        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, notification)

        return Result.success()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Daily Digest", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Daily spending summary"
        }
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "digest"
        private const val NOTIFICATION_ID = 2001
        private const val WORK_NAME = "daily_digest"

        fun schedule(context: Context) {
            // Calculate delay to 8 PM today (or tomorrow if past 8 PM)
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, AppConfig.DIGEST_HOUR); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val delay = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<DigestWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
            Log.d("DigestWorker", "Daily digest scheduled")
        }
    }
}
