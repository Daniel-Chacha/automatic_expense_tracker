package com.personal.expensetracker.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.remote.TransactionRepository
import com.personal.expensetracker.data.remote.TransactionRepository.SyncOutcome
import com.personal.expensetracker.util.AppConfig
import java.util.concurrent.TimeUnit

/**
 * Periodically pushes unsynced transactions to Neon.
 * Runs every AppConfig.SYNC_INTERVAL_MINUTES when the device has network.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        return when (val outcome = TransactionRepository(db).syncTransactions()) {
            is SyncOutcome.Success -> {
                Log.d(TAG, "Sync complete: ${outcome.count} transactions pushed")
                Result.success()
            }
            is SyncOutcome.TransientFailure -> {
                Log.w(TAG, "Transient sync failure (${outcome.reason}), will retry")
                Result.retry()
            }
            is SyncOutcome.PermanentFailure -> {
                Log.e(TAG, "Permanent sync failure (${outcome.reason})")
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "expense_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                AppConfig.SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.d(TAG, "Periodic sync scheduled (every ${AppConfig.SYNC_INTERVAL_MINUTES} min, when connected)")
        }

        fun syncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
