package com.personal.expensetracker.data.remote

import android.util.Log
import com.personal.expensetracker.data.local.AppDatabase
import io.github.jan.supabase.postgrest.from

/**
 * Handles syncing local Room data to Supabase Postgres.
 * Strategy: push unsynced transactions → mark as synced.
 * Single-device, single-user — no conflict resolution needed.
 */
class SyncManager(private val db: AppDatabase) {

    companion object {
        private const val TAG = "SyncManager"
    }

    /**
     * Push all unsynced transactions to Supabase.
     * Returns the number of transactions synced, or -1 on failure.
     */
    suspend fun syncTransactions(): Int {
        return try {
            val unsynced = db.transactionDao().getUnsynced()
            if (unsynced.isEmpty()) {
                Log.d(TAG, "No unsynced transactions")
                return 0
            }

            val dtos = unsynced.map { it.toDto() }

            // Upsert to Supabase (insert or update by id)
            SupabaseClient.instance.from("transactions").upsert(dtos)

            // Mark all as synced locally
            unsynced.forEach { db.transactionDao().markSynced(it.id) }

            Log.d(TAG, "Synced ${unsynced.size} transactions")
            unsynced.size
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            -1
        }
    }
}
