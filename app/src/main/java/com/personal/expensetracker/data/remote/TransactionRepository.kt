package com.personal.expensetracker.data.remote

import android.util.Log
import androidx.room.withTransaction
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.entity.Transaction
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant

/**
 * Pushes local unsynced transactions to Neon over JDBC and marks them synced
 * atomically.
 */
class TransactionRepository(private val db: AppDatabase) {

    sealed class SyncOutcome {
        data class Success(val count: Int) : SyncOutcome()
        data class TransientFailure(val reason: String) : SyncOutcome()
        data class PermanentFailure(val reason: String) : SyncOutcome()
    }

    suspend fun syncTransactions(): SyncOutcome {
        if (!NeonClient.isConfigured) {
            return SyncOutcome.PermanentFailure("NEON_JDBC_URL not configured")
        }

        val unsynced = db.transactionDao().getUnsynced()
        if (unsynced.isEmpty()) {
            Log.d(TAG, "No unsynced transactions")
            return SyncOutcome.Success(0)
        }

        return try {
            val persistedIds = NeonClient.withConnection { conn ->
                upsertBatch(conn, unsynced)
            }

            db.withTransaction {
                persistedIds.forEach { db.transactionDao().markSynced(it) }
            }

            Log.d(TAG, "Synced ${persistedIds.size} of ${unsynced.size} transactions")
            SyncOutcome.Success(persistedIds.size)
        } catch (e: SQLException) {
            db.transactionDao().recordSyncFailure(unsynced.map { it.id }, System.currentTimeMillis())
            categorize(e, unsynced.size)
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected sync error", t)
            db.transactionDao().recordSyncFailure(unsynced.map { it.id }, System.currentTimeMillis())
            SyncOutcome.TransientFailure(t.message ?: "unknown")
        }
    }

    private fun categorize(e: SQLException, batchSize: Int): SyncOutcome {
        val state = e.sqlState.orEmpty()
        return when {
            state.startsWith("08") -> {
                Log.w(TAG, "Network/connection error ($state) on $batchSize rows", e)
                SyncOutcome.TransientFailure("connection")
            }
            state.startsWith("23") -> {
                Log.e(TAG, "Integrity error ($state) on $batchSize rows", e)
                SyncOutcome.PermanentFailure("integrity")
            }
            state.startsWith("42") -> {
                Log.e(TAG, "Schema mismatch ($state) on $batchSize rows", e)
                SyncOutcome.PermanentFailure("schema-drift")
            }
            else -> {
                Log.e(TAG, "Unclassified SQL error ($state) on $batchSize rows", e)
                SyncOutcome.TransientFailure(state.ifEmpty { "unknown" })
            }
        }
    }

    private fun upsertBatch(conn: Connection, rows: List<Transaction>): List<Int> {
        val sql = """
            INSERT INTO transactions
                (id, type, status, amount, category_id, account_id,
                 description, transacted_at, meta, reference, counterparty,
                 dedup_hash, created_at, last_sync_attempt_at, sync_failures)
            VALUES (?, ?::transaction_type, ?::transaction_status, ?, ?, ?,
                    ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                type = EXCLUDED.type,
                status = EXCLUDED.status,
                amount = EXCLUDED.amount,
                category_id = EXCLUDED.category_id,
                account_id = EXCLUDED.account_id,
                description = EXCLUDED.description,
                transacted_at = EXCLUDED.transacted_at,
                meta = EXCLUDED.meta,
                reference = EXCLUDED.reference,
                counterparty = EXCLUDED.counterparty,
                dedup_hash = EXCLUDED.dedup_hash
            RETURNING id
        """.trimIndent()

        val landed = mutableListOf<Int>()
        conn.autoCommit = false
        try {
            conn.prepareStatement(sql).use { ps ->
                rows.forEach { row ->
                    ps.setInt(1, row.id)
                    ps.setString(2, row.type.name)
                    ps.setString(3, row.status.name)
                    ps.setInt(4, row.amount)
                    if (row.categoryId != null) ps.setInt(5, row.categoryId) else ps.setNull(5, java.sql.Types.SMALLINT)
                    if (row.accountId != null) ps.setInt(6, row.accountId) else ps.setNull(6, java.sql.Types.SMALLINT)
                    ps.setString(7, row.description)
                    ps.setTimestamp(8, Timestamp.from(Instant.ofEpochMilli(row.transactedAt)))
                    ps.setObject(9, jsonb(row.meta))
                    ps.setString(10, row.reference)
                    ps.setString(11, row.counterparty)
                    ps.setString(12, row.dedupHash)
                    ps.setTimestamp(13, Timestamp.from(Instant.ofEpochMilli(row.createdAt)))
                    if (row.lastSyncAttemptAt != null) {
                        ps.setTimestamp(14, Timestamp.from(Instant.ofEpochMilli(row.lastSyncAttemptAt)))
                    } else {
                        ps.setNull(14, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                    }
                    ps.setInt(15, row.syncFailures)

                    ps.executeQuery().use { rs ->
                        if (rs.next()) landed += rs.getInt(1)
                    }
                }
            }
            conn.commit()
        } catch (t: Throwable) {
            runCatching { conn.rollback() }
            throw t
        } finally {
            conn.autoCommit = true
        }
        return landed
    }

    private fun jsonb(json: String?): PGobject? {
        if (json.isNullOrBlank()) return null
        return PGobject().apply {
            type = "jsonb"
            value = json
        }
    }

    companion object {
        private const val TAG = "TxnRepository"
    }
}
