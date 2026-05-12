package com.personal.financetracker.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.remote.TransactionRepository
import com.personal.financetracker.data.remote.TransactionRepository.SyncOutcome
import com.personal.financetracker.util.AppConfig
import com.personal.financetracker.util.SyncState
import java.util.concurrent.TimeUnit

/**
 * Pushes unsynced transactions to Neon, then prunes synced rows older than
 * [AppConfig.LOCAL_RETENTION_DAYS] to save phone storage.
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
                SyncState.recordSuccess(applicationContext, outcome.count)
                pruneOldLocalRows(db)
                Result.success()
            }
            is SyncOutcome.TransientFailure -> {
                Log.w(TAG, "Transient sync failure (${outcome.reason}), will retry")
                SyncState.recordTransientFailure(applicationContext, outcome.reason)
                Result.retry()
            }
            is SyncOutcome.PermanentFailure -> {
                Log.e(TAG, "Permanent sync failure (${outcome.reason})")
                SyncState.recordPermanentFailure(applicationContext, outcome.reason)
                Result.failure()
            }
        }
    }

    private suspend fun pruneOldLocalRows(db: AppDatabase) {
        val cutoff = System.currentTimeMillis() -
            AppConfig.LOCAL_RETENTION_DAYS * 24L * 60L * 60L * 1000L
        val removed = db.transactionDao().pruneSyncedBefore(cutoff)
        if (removed > 0) {
            Log.d(TAG, "Pruned $removed local synced rows older than ${AppConfig.LOCAL_RETENTION_DAYS}d")
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "expense_sync"
        private const val IMMEDIATE_WORK_NAME = "expense_sync_immediate"

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

        /**
         * Queue a one-shot sync. WorkManager will run it as soon as network
         * is available — used both for the manual "Sync Now" button and the
         * connectivity-triggered listener in [FinanceTrackerApp].
         */
        fun syncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
