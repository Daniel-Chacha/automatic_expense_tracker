package com.personal.financetracker.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.MonthlySnapshot
import com.personal.financetracker.data.local.entity.SnapshotKind
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Records end-of-month totals for Savings and Investments into the
 * `monthly_snapshots` table. Runs daily — the upsert key (year, month, kind)
 * makes re-runs idempotent, so the *last* run of the calendar month
 * effectively captures the EOM value.
 *
 * Past months without a snapshot stay blank in the yearly chart on purpose —
 * we never invent historical data.
 */
class MonthlySnapshotWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1 // 1..12

            val savings = db.savingsGoalDao().getCurrentTotal()
            val investments = db.investmentDao().getCurrentTotal()

            db.snapshotDao().upsert(
                MonthlySnapshot(year = year, month = month, kind = SnapshotKind.SAVINGS, amount = savings)
            )
            db.snapshotDao().upsert(
                MonthlySnapshot(year = year, month = month, kind = SnapshotKind.INVESTMENT, amount = investments)
            )

            Log.d(TAG, "Snapshot recorded for $year-$month: savings=$savings investments=$investments")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Snapshot worker failed", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MonthlySnapshotWorker"
        private const val WORK_NAME = "monthly_snapshot"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonthlySnapshotWorker>(
                1, TimeUnit.DAYS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
