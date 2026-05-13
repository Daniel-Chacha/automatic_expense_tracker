package com.personal.financetracker.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.remote.DeletionSyncRepository
import com.personal.financetracker.data.remote.MetadataSyncRepository
import com.personal.financetracker.data.remote.PullSyncRepository
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

        // 1. PUSH upserts (creates + updates) first so any pending local writes
        //    reach Neon before we mirror back.
        val txnOutcome = TransactionRepository(db).syncTransactions()
        val metaOutcome = MetadataSyncRepository(db, applicationContext).syncAll()
        // 2. DRAIN the hard-delete outbox. Must run after push (so an update
        //    we're about to also delete doesn't get re-inserted on the next
        //    pull) and before pull (so the deleted rows aren't refetched).
        val deletionOutcome = DeletionSyncRepository(db).drainPendingDeletions()
        // 3. PULL last so Room becomes a current mirror of Neon.
        val pullOutcome = PullSyncRepository(db, applicationContext).pullAll()

        val combined = combine(txnOutcome, metaOutcome, deletionOutcome, pullOutcome)
        return when (combined) {
            is CombinedOutcome.Success -> {
                Log.d(TAG, "Sync complete: pushed ${combined.txnCount}+${combined.metaCount}, deleted ${combined.deletedCount}, pulled ${combined.pulledCount}")
                SyncState.recordSuccess(applicationContext, combined.txnCount + combined.metaCount)
                // Note: deliberately do NOT prune local rows. With the
                // local-mirror-of-Neon model, anything we prune would be
                // re-fetched on the next pull. Room grows monotonically to
                // match Neon — that's the intended state.
                Result.success()
            }
            is CombinedOutcome.Transient -> {
                Log.w(TAG, "Transient sync failure (${combined.reason}), will retry")
                SyncState.recordTransientFailure(applicationContext, combined.reason)
                Result.retry()
            }
            is CombinedOutcome.Permanent -> {
                Log.e(TAG, "Permanent sync failure (${combined.reason})")
                SyncState.recordPermanentFailure(applicationContext, combined.reason)
                Result.failure()
            }
        }
    }

    private sealed class CombinedOutcome {
        data class Success(val txnCount: Int, val metaCount: Int, val deletedCount: Int, val pulledCount: Int) : CombinedOutcome()
        data class Transient(val reason: String) : CombinedOutcome()
        data class Permanent(val reason: String) : CombinedOutcome()
    }

    private fun combine(
        txn: TransactionRepository.SyncOutcome,
        meta: MetadataSyncRepository.SyncOutcome,
        del: DeletionSyncRepository.DeleteOutcome,
        pull: PullSyncRepository.PullOutcome
    ): CombinedOutcome {
        if (txn is SyncOutcome.PermanentFailure) return CombinedOutcome.Permanent("transactions:${txn.reason}")
        if (meta is MetadataSyncRepository.SyncOutcome.PermanentFailure) return CombinedOutcome.Permanent("metadata:${meta.reason}")
        if (del is DeletionSyncRepository.DeleteOutcome.PermanentFailure) return CombinedOutcome.Permanent("deletions:${del.reason}")
        if (pull is PullSyncRepository.PullOutcome.PermanentFailure) return CombinedOutcome.Permanent("pull:${pull.reason}")
        if (txn is SyncOutcome.TransientFailure) return CombinedOutcome.Transient("transactions:${txn.reason}")
        if (meta is MetadataSyncRepository.SyncOutcome.TransientFailure) return CombinedOutcome.Transient("metadata:${meta.reason}")
        if (del is DeletionSyncRepository.DeleteOutcome.TransientFailure) return CombinedOutcome.Transient("deletions:${del.reason}")
        if (pull is PullSyncRepository.PullOutcome.TransientFailure) return CombinedOutcome.Transient("pull:${pull.reason}")
        val txnCount = (txn as? SyncOutcome.Success)?.count ?: 0
        val metaCount = (meta as? MetadataSyncRepository.SyncOutcome.Success)?.pushed ?: 0
        val deletedCount = (del as? DeletionSyncRepository.DeleteOutcome.Success)?.deleted ?: 0
        val pulledCount = (pull as? PullSyncRepository.PullOutcome.Success)?.pulled ?: 0
        return CombinedOutcome.Success(txnCount, metaCount, deletedCount, pulledCount)
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
