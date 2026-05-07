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
 * Daily check that posts a notification for any category whose month-to-date
 * spend has crossed AppConfig.BUDGET_ALERT_THRESHOLD of its budget.
 */
class BudgetAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val cal = Calendar.getInstance()
            val monthStart = FormatUtils.getMonthStart(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            val monthEnd = FormatUtils.getMonthEnd(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))

            val budgets = db.budgetDao().getByMonth(monthStart).first()
            if (budgets.isEmpty()) return Result.success()

            val spent = db.transactionDao().getSpentByCategory(monthStart, monthEnd).first()
                .associate { it.categoryId to it.total }
            val categories = db.categoryDao().getAll().first().associateBy { it.id }

            ensureChannel()
            budgets.forEach { budget ->
                val total = spent[budget.categoryId] ?: 0
                val ratio = total.toDouble() / budget.amount.coerceAtLeast(1)
                if (ratio >= AppConfig.BUDGET_ALERT_THRESHOLD) {
                    val cat = categories[budget.categoryId]?.name ?: "Category"
                    notifyOverrun(cat, total, budget.amount, ratio, budget.id)
                }
            }
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "BudgetAlertWorker failed", t)
            Result.retry()
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Budget alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notifies when a category's spend crosses the budget threshold." }
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun notifyOverrun(category: String, spent: Int, budget: Int, ratio: Double, idSeed: Int) {
        val pct = (ratio * 100).toInt()
        val title = if (ratio >= 1.0) "Budget exceeded" else "Budget at $pct%"
        val text = "$category: ${FormatUtils.formatAmount(spent)} of ${FormatUtils.formatAmount(budget)}"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID_BASE + idSeed, notification)
    }

    companion object {
        private const val TAG = "BudgetAlertWorker"
        private const val CHANNEL_ID = "budget_alerts"
        private const val NOTIFICATION_ID_BASE = 3000
        private const val WORK_NAME = "budget_alerts"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BudgetAlertWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
