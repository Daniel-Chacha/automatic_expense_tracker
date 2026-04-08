package com.personal.expensetracker.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.remote.SyncManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically syncs unsynced transactions to Supabase.
 * Runs every 15 minutes when the device has network connectivity.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val syncManager = SyncManager(db)

        val count = syncManager.syncTransactions()
        return if (count >= 0) {
            Log.d(TAG, "Sync complete: $count transactions pushed")
            Result.success()
        } else {
            Log.w(TAG, "Sync failed, will retry")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "expense_sync"

        /**
         * Schedule periodic sync every 15 minutes when connected.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
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

            Log.d(TAG, "Periodic sync scheduled (every 15 min, when connected)")
        }

        /**
         * Trigger an immediate one-shot sync.
         */
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
