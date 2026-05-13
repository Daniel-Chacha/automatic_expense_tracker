package com.personal.financetracker.data.remote

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.*
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Pulls rows from Neon down into Room. Used to keep Room as a transparent
 * cache of Neon — reads in the UI stay fast (local SQLite) but the cloud is
 * the source of truth for what data exists.
 *
 * Runs after [TransactionRepository] and [MetadataSyncRepository] have pushed,
 * so any rows we'd overwrite locally have already been pushed up. Then we
 * fetch everything that's been modified on Neon since our last successful
 * pull and bulk-insert (REPLACE on conflict).
 *
 * On a fresh install with empty Room, the watermark is 0 → we fetch every
 * row that exists in Neon → Room is fully populated. That's the "restore
 * after uninstall" path: it just falls out of the same mechanism.
 */
class PullSyncRepository(
    private val db: AppDatabase,
    appContext: Context
) {
    private val prefs = appContext.getSharedPreferences("pull_sync", Context.MODE_PRIVATE)

    sealed class PullOutcome {
        data class Success(val pulled: Int) : PullOutcome()
        data class TransientFailure(val reason: String) : PullOutcome()
        data class PermanentFailure(val reason: String) : PullOutcome()
    }

    suspend fun pullAll(): PullOutcome {
        if (!NeonClient.isConfigured) {
            return PullOutcome.PermanentFailure("NEON_JDBC_URL not configured")
        }

        return try {
            // 1. Fetch rows from Neon (no Room writes inside connection block).
            val txns = NeonClient.withConnection { conn -> fetchTransactions(conn, lastPulled("transactions")) }
            val cats = NeonClient.withConnection { conn -> fetchCategories(conn, lastPulled("categories")) }
            val buds = NeonClient.withConnection { conn -> fetchBudgets(conn, lastPulled("budgets")) }
            val goals = NeonClient.withConnection { conn -> fetchSavingsGoals(conn, lastPulled("savings_goals")) }
            val invs = NeonClient.withConnection { conn -> fetchInvestments(conn, lastPulled("investments")) }
            val debts = NeonClient.withConnection { conn -> fetchDebts(conn, lastPulled("debts")) }
            val snaps = NeonClient.withConnection { conn -> fetchSnapshots(conn, lastPulled("monthly_snapshots")) }

            val total = txns.size + cats.size + buds.size + goals.size + invs.size + debts.size + snaps.size
            if (total == 0) {
                Log.d(TAG, "Pull: up to date")
                return PullOutcome.Success(0)
            }

            // 2. Apply everything in a single Room transaction.
            db.withTransaction {
                if (cats.isNotEmpty()) cats.forEach { db.categoryDao().insert(it) }
                if (txns.isNotEmpty()) txns.forEach { db.transactionDao().insert(it) }
                if (buds.isNotEmpty()) buds.forEach { db.budgetDao().insert(it) }
                if (goals.isNotEmpty()) goals.forEach { db.savingsGoalDao().insert(it) }
                if (invs.isNotEmpty()) invs.forEach { db.investmentDao().insert(it) }
                if (debts.isNotEmpty()) debts.forEach { db.debtDao().insert(it) }
                if (snaps.isNotEmpty()) snaps.forEach { db.snapshotDao().upsert(it) }
            }

            // 3. Advance watermarks.
            if (txns.isNotEmpty()) setLastPulled("transactions", txns.maxOf { it.updatedAt })
            if (cats.isNotEmpty()) setLastPulled("categories", cats.maxOf { it.updatedAt })
            if (buds.isNotEmpty()) setLastPulled("budgets", buds.maxOf { it.updatedAt })
            if (goals.isNotEmpty()) setLastPulled("savings_goals", goals.maxOf { it.updatedAt })
            if (invs.isNotEmpty()) setLastPulled("investments", invs.maxOf { it.updatedAt })
            if (debts.isNotEmpty()) setLastPulled("debts", debts.maxOf { it.updatedAt })
            if (snaps.isNotEmpty()) setLastPulled("monthly_snapshots", snaps.maxOf { it.recordedAt })

            Log.d(TAG, "Pull complete: $total rows mirrored from Neon")
            PullOutcome.Success(total)
        } catch (e: SQLException) {
            categorize(e)
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected pull error", t)
            PullOutcome.TransientFailure(t.message ?: "unknown")
        }
    }

    // ── Per-table fetch + parse ─────────────────────────

    private fun fetchTransactions(conn: java.sql.Connection, since: Long): List<Transaction> {
        val sql = """
            SELECT id, type, status, amount, category_id, description,
                   transacted_at, meta, reference, counterparty, dedup_hash,
                   created_at, last_sync_attempt_at, sync_failures, updated_at
            FROM transactions WHERE updated_at > ? ORDER BY updated_at
        """.trimIndent()
        val out = mutableListOf<Transaction>()
        conn.prepareStatement(sql).use { ps ->
            ps.setTimestamp(1, java.sql.Timestamp(since))
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out += Transaction(
                        id = rs.getInt("id"),
                        type = TransactionType.valueOf(rs.getString("type")),
                        status = TransactionStatus.valueOf(rs.getString("status")),
                        amount = rs.getInt("amount"),
                        categoryId = rs.getObject("category_id") as? Int,
                        description = rs.getString("description"),
                        transactedAt = rs.getTimestamp("transacted_at").time,
                        meta = rs.getString("meta"),
                        reference = rs.getString("reference"),
                        counterparty = rs.getString("counterparty"),
                        dedupHash = rs.getString("dedup_hash"),
                        createdAt = rs.getTimestamp("created_at").time,
                        // Anything pulled from Neon is by definition synced.
                        isSynced = true,
                        lastSyncAttemptAt = rs.getTimestamp("last_sync_attempt_at")?.time,
                        syncFailures = rs.getInt("sync_failures"),
                        updatedAt = rs.getTimestamp("updated_at").time
                    )
                }
            }
        }
        return out
    }

    private fun fetchCategories(conn: java.sql.Connection, since: Long): List<Category> {
        val sql = """
            SELECT id, name, icon, color, is_income, created_at, updated_at
            FROM categories WHERE updated_at > ? ORDER BY updated_at
        """.trimIndent()
        val out = mutableListOf<Category>()
        conn.prepareStatement(sql).use { ps ->
            ps.setTimestamp(1, java.sql.Timestamp(since))
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out += Category(
                        id = rs.getInt("id"),
                        name = rs.getString("name"),
                        icon = rs.getString("icon"),
                        color = rs.getString("color"),
                        isIncome = rs.getBoolean("is_income"),
                        createdAt = rs.getTimestamp("created_at").time,
                        updatedAt = rs.getTimestamp("updated_at").time
                    )
                }
            }
        }
        return out
    }

    private fun fetchBudgets(conn: java.sql.Connection, since: Long): List<Budget> {
        val sql = """
            SELECT id, category_id, month, amount, updated_at
            FROM budgets WHERE updated_at > ? ORDER BY updated_at
        """.trimIndent()
        val out = mutableListOf<Budget>()
        conn.prepareStatement(sql).use { ps ->
            ps.setTimestamp(1, java.sql.Timestamp(since))
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out += Budget(
                        id = rs.getInt("id"),
                        categoryId = rs.getInt("category_id"),
                        month = rs.getDate("month").time,
                        amount = rs.getInt("amount"),
                        updatedAt = rs.getTimestamp("updated_at").time
                    )
                }
            }
        }
        return out
    }

    private fun fetchSavingsGoals(conn: java.sql.Connection, since: Long): List<SavingsGoal> {
        val sql = """
            SELECT id, name, target_amount, current_amount, deadline, is_completed,
                   created_at, updated_at
            FROM savings_goals WHERE updated_at > ? ORDER BY updated_at
        """.trimIndent()
        val out = mutableListOf<SavingsGoal>()
        conn.prepareStatement(sql).use { ps ->
            ps.setTimestamp(1, java.sql.Timestamp(since))
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out += SavingsGoal(
                        id = rs.getInt("id"),
                        name = rs.getString("name"),
                        targetAmount = rs.getInt("target_amount"),
                        currentAmount = rs.getInt("current_amount"),
                        deadline = rs.getDate("deadline")?.time,
                        isCompleted = rs.getBoolean("is_completed"),
                        createdAt = rs.getTimestamp("created_at").time,
                        updatedAt = rs.getTimestamp("updated_at").time
                    )
                }
            }
        }
        return out
    }

    private fun fetchInvestments(conn: java.sql.Connection, since: Long): List<Investment> {
        val sql = """
            SELECT id, name, type, buy_in_amount, current_value, notes,
                   updated_at, created_at
            FROM investments WHERE updated_at > ? ORDER BY updated_at
        """.trimIndent()
        val out = mutableListOf<Investment>()
        conn.prepareStatement(sql).use { ps ->
            ps.setTimestamp(1, java.sql.Timestamp(since))
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out += Investment(
                        id = rs.getInt("id"),
                        name = rs.getString("name"),
                        type = rs.getString("type"),
                        buyInAmount = rs.getInt("buy_in_amount"),
                        currentValue = rs.getInt("current_value"),
                        notes = rs.getString("notes"),
                        updatedAt = rs.getTimestamp("updated_at").time,
                        createdAt = rs.getTimestamp("created_at").time
                    )
                }
            }
        }
        return out
    }

    private fun fetchDebts(conn: java.sql.Connection, since: Long): List<Debt> {
        val sql = """
            SELECT id, direction, person, amount, description, is_settled,
                   due_date, created_at, updated_at
            FROM debts WHERE updated_at > ? ORDER BY updated_at
        """.trimIndent()
        val out = mutableListOf<Debt>()
        conn.prepareStatement(sql).use { ps ->
            ps.setTimestamp(1, java.sql.Timestamp(since))
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out += Debt(
                        id = rs.getInt("id"),
                        direction = DebtDirection.valueOf(rs.getString("direction")),
                        person = rs.getString("person"),
                        amount = rs.getInt("amount"),
                        description = rs.getString("description"),
                        isSettled = rs.getBoolean("is_settled"),
                        dueDate = rs.getDate("due_date")?.time,
                        createdAt = rs.getTimestamp("created_at").time,
                        updatedAt = rs.getTimestamp("updated_at").time
                    )
                }
            }
        }
        return out
    }

    private fun fetchSnapshots(conn: java.sql.Connection, since: Long): List<MonthlySnapshot> {
        val sql = """
            SELECT id, year, month, kind, amount, recorded_at
            FROM monthly_snapshots WHERE recorded_at > ? ORDER BY recorded_at
        """.trimIndent()
        val out = mutableListOf<MonthlySnapshot>()
        conn.prepareStatement(sql).use { ps ->
            ps.setTimestamp(1, java.sql.Timestamp(since))
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out += MonthlySnapshot(
                        id = rs.getInt("id"),
                        year = rs.getInt("year"),
                        month = rs.getInt("month"),
                        kind = SnapshotKind.valueOf(rs.getString("kind")),
                        amount = rs.getInt("amount"),
                        recordedAt = rs.getTimestamp("recorded_at").time
                    )
                }
            }
        }
        return out
    }

    // ── helpers ──────────────────────────────────────────

    private fun lastPulled(table: String): Long = prefs.getLong("last_$table", 0L)

    private fun setLastPulled(table: String, value: Long) {
        prefs.edit().putLong("last_$table", value).apply()
    }

    private fun categorize(e: SQLException): PullOutcome {
        val state = e.sqlState.orEmpty()
        return when {
            state.startsWith("08") -> {
                Log.w(TAG, "Network/connection error during pull ($state)", e)
                PullOutcome.TransientFailure("connection")
            }
            state.startsWith("42") -> {
                Log.e(TAG, "Schema mismatch during pull ($state)", e)
                PullOutcome.PermanentFailure("schema-drift")
            }
            else -> {
                Log.e(TAG, "Unclassified SQL error during pull ($state)", e)
                PullOutcome.TransientFailure(state.ifEmpty { "unknown" })
            }
        }
    }

    companion object {
        private const val TAG = "PullSync"
    }
}
