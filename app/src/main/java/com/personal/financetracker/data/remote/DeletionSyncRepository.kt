package com.personal.financetracker.data.remote

import android.util.Log
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.PendingDeletion
import java.sql.SQLException

/**
 * Drains the local `pending_deletions` outbox by issuing matching DELETE
 * statements against Neon. Only this repository (and a developer running
 * raw `psql`) can remove a row from Neon — see the explicit allow-list of
 * tables below.
 *
 * Runs between the push and pull steps of [SyncWorker] so:
 *   1. Pending creates/updates land in Neon first (push step)
 *   2. Pending deletes are then applied to Neon (this step)
 *   3. The subsequent pull mirrors the post-delete state down to Room
 */
class DeletionSyncRepository(private val db: AppDatabase) {

    sealed class DeleteOutcome {
        data class Success(val deleted: Int) : DeleteOutcome()
        data class TransientFailure(val reason: String) : DeleteOutcome()
        data class PermanentFailure(val reason: String) : DeleteOutcome()
    }

    /**
     * Tables we accept DELETEs for. A pending row pointing at any other table
     * is dropped from the outbox with a warning rather than executed — this
     * stops a corrupted entry from running arbitrary SQL.
     */
    private val allowedTables = setOf(
        "transactions", "categories", "budgets",
        "savings_goals", "investments", "debts", "monthly_snapshots"
    )

    suspend fun drainPendingDeletions(): DeleteOutcome {
        if (!NeonClient.isConfigured) {
            return DeleteOutcome.PermanentFailure("NEON_JDBC_URL not configured")
        }

        val pending = db.pendingDeletionDao().getAll()
        if (pending.isEmpty()) {
            return DeleteOutcome.Success(0)
        }

        val processed = mutableListOf<Int>()
        return try {
            NeonClient.withConnection { conn ->
                conn.autoCommit = false
                try {
                    pending.forEach { p ->
                        if (p.tableName !in allowedTables) {
                            Log.w(TAG, "Dropping pending deletion with unknown table=${p.tableName}")
                            processed += p.id
                            return@forEach
                        }
                        // Table name is allow-listed; safe to interpolate.
                        val sql = "DELETE FROM ${p.tableName} WHERE id = ?"
                        conn.prepareStatement(sql).use { ps ->
                            ps.setInt(1, p.rowId)
                            ps.executeUpdate()
                        }
                        processed += p.id
                    }
                    conn.commit()
                } catch (t: Throwable) {
                    runCatching { conn.rollback() }
                    throw t
                } finally {
                    conn.autoCommit = true
                }
            }
            // Remove from the outbox only after Neon has committed.
            processed.forEach { db.pendingDeletionDao().remove(it) }
            Log.d(TAG, "Drained ${processed.size} pending deletions")
            DeleteOutcome.Success(processed.size)
        } catch (e: SQLException) {
            categorize(e)
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected deletion-sync error", t)
            DeleteOutcome.TransientFailure(t.message ?: "unknown")
        }
    }

    private fun categorize(e: SQLException): DeleteOutcome {
        val state = e.sqlState.orEmpty()
        return when {
            state.startsWith("08") -> {
                Log.w(TAG, "Network error during deletion drain ($state)", e)
                DeleteOutcome.TransientFailure("connection")
            }
            state.startsWith("42") -> {
                Log.e(TAG, "Schema mismatch during deletion drain ($state)", e)
                DeleteOutcome.PermanentFailure("schema-drift")
            }
            else -> {
                Log.e(TAG, "Unclassified SQL error during deletion drain ($state)", e)
                DeleteOutcome.TransientFailure(state.ifEmpty { "unknown" })
            }
        }
    }

    companion object {
        private const val TAG = "DeletionSync"
    }
}
