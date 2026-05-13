package com.personal.financetracker.data.remote

import android.content.Context
import android.util.Log
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.Budget
import com.personal.financetracker.data.local.entity.Category
import com.personal.financetracker.data.local.entity.Debt
import com.personal.financetracker.data.local.entity.Investment
import com.personal.financetracker.data.local.entity.MonthlySnapshot
import com.personal.financetracker.data.local.entity.SavingsGoal
import java.sql.Connection
import java.sql.Date
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pushes Categories / Budgets / SavingsGoals / Investments / Debts /
 * MonthlySnapshots to Neon. Transactions are still owned by
 * [TransactionRepository].
 *
 * Tracks per-table sync watermarks (`updated_at`) in SharedPreferences so
 * each run only sends rows that changed since the last successful push.
 *
 * This repo only handles creates and updates. Hard deletes are processed
 * separately by DeletionSyncRepository, which drains the
 * `pending_deletions` outbox and issues DELETE FROM <table> WHERE id = ?
 * against Neon.
 */
class MetadataSyncRepository(
    private val db: AppDatabase,
    appContext: Context
) {
    private val prefs = appContext.getSharedPreferences("metadata_sync", Context.MODE_PRIVATE)

    sealed class SyncOutcome {
        data class Success(val pushed: Int) : SyncOutcome()
        data class TransientFailure(val reason: String) : SyncOutcome()
        data class PermanentFailure(val reason: String) : SyncOutcome()
    }

    suspend fun syncAll(): SyncOutcome {
        if (!NeonClient.isConfigured) {
            return SyncOutcome.PermanentFailure("NEON_JDBC_URL not configured")
        }

        // 1. Read pending rows from Room (suspending; outside JDBC connection).
        val pendingCategories = db.categoryDao().getModifiedSince(lastSyncedAt("categories"))
        val pendingBudgets = db.budgetDao().getModifiedSince(lastSyncedAt("budgets"))
        val pendingGoals = db.savingsGoalDao().getModifiedSince(lastSyncedAt("savings_goals"))
        val pendingInvestments = db.investmentDao().getModifiedSince(lastSyncedAt("investments"))
        val pendingDebts = db.debtDao().getModifiedSince(lastSyncedAt("debts"))
        val pendingSnapshots = db.snapshotDao().getModifiedSince(lastSyncedAt("monthly_snapshots"))

        val totalToPush = pendingCategories.size + pendingBudgets.size + pendingGoals.size +
            pendingInvestments.size + pendingDebts.size + pendingSnapshots.size
        if (totalToPush == 0) {
            Log.d(TAG, "Metadata sync: nothing to push")
            return SyncOutcome.Success(0)
        }

        // 2. Push within a single transaction. No suspend calls inside.
        return try {
            NeonClient.withConnection { conn ->
                conn.autoCommit = false
                try {
                    if (pendingCategories.isNotEmpty()) pushCategories(conn, pendingCategories)
                    if (pendingBudgets.isNotEmpty()) pushBudgets(conn, pendingBudgets)
                    if (pendingGoals.isNotEmpty()) pushSavingsGoals(conn, pendingGoals)
                    if (pendingInvestments.isNotEmpty()) pushInvestments(conn, pendingInvestments)
                    if (pendingDebts.isNotEmpty()) pushDebts(conn, pendingDebts)
                    if (pendingSnapshots.isNotEmpty()) pushSnapshots(conn, pendingSnapshots)
                    conn.commit()
                } catch (t: Throwable) {
                    runCatching { conn.rollback() }
                    throw t
                } finally {
                    conn.autoCommit = true
                }
            }
            // 3. Advance watermarks only after the entire batch committed.
            if (pendingCategories.isNotEmpty()) setLastSyncedAt("categories", pendingCategories.maxOf { it.updatedAt })
            if (pendingBudgets.isNotEmpty()) setLastSyncedAt("budgets", pendingBudgets.maxOf { it.updatedAt })
            if (pendingGoals.isNotEmpty()) setLastSyncedAt("savings_goals", pendingGoals.maxOf { it.updatedAt })
            if (pendingInvestments.isNotEmpty()) setLastSyncedAt("investments", pendingInvestments.maxOf { it.updatedAt })
            if (pendingDebts.isNotEmpty()) setLastSyncedAt("debts", pendingDebts.maxOf { it.updatedAt })
            if (pendingSnapshots.isNotEmpty()) setLastSyncedAt("monthly_snapshots", pendingSnapshots.maxOf { it.recordedAt })

            Log.d(TAG, "Metadata sync complete: $totalToPush rows pushed")
            SyncOutcome.Success(totalToPush)
        } catch (e: SQLException) {
            categorize(e)
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected metadata sync error", t)
            SyncOutcome.TransientFailure(t.message ?: "unknown")
        }
    }

    // ── per-table push -----------------------------------

    private fun pushCategories(conn: Connection, rows: List<Category>) {
        val sql = """
            INSERT INTO categories
                (id, name, icon, color, is_income, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                icon = EXCLUDED.icon,
                color = EXCLUDED.color,
                is_income = EXCLUDED.is_income,
                updated_at = EXCLUDED.updated_at
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            rows.forEach { c ->
                ps.setInt(1, c.id)
                ps.setString(2, c.name)
                ps.setString(3, c.icon)
                ps.setString(4, c.color)
                ps.setBoolean(5, c.isIncome)
                ps.setTimestamp(6, timestamp(c.createdAt))
                ps.setTimestamp(7, timestamp(c.updatedAt))
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun pushBudgets(conn: Connection, rows: List<Budget>) {
        val sql = """
            INSERT INTO budgets (id, category_id, month, amount, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                category_id = EXCLUDED.category_id,
                month = EXCLUDED.month,
                amount = EXCLUDED.amount,
                updated_at = EXCLUDED.updated_at
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            rows.forEach { b ->
                ps.setInt(1, b.id)
                ps.setInt(2, b.categoryId)
                ps.setDate(3, sqlDate(b.month))
                ps.setInt(4, b.amount)
                ps.setTimestamp(5, timestamp(b.updatedAt))
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun pushSavingsGoals(conn: Connection, rows: List<SavingsGoal>) {
        val sql = """
            INSERT INTO savings_goals
                (id, name, target_amount, current_amount, deadline, is_completed, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                target_amount = EXCLUDED.target_amount,
                current_amount = EXCLUDED.current_amount,
                deadline = EXCLUDED.deadline,
                is_completed = EXCLUDED.is_completed,
                updated_at = EXCLUDED.updated_at
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            rows.forEach { g ->
                ps.setInt(1, g.id)
                ps.setString(2, g.name)
                ps.setInt(3, g.targetAmount)
                ps.setInt(4, g.currentAmount)
                if (g.deadline != null) ps.setDate(5, sqlDate(g.deadline)) else ps.setNull(5, java.sql.Types.DATE)
                ps.setBoolean(6, g.isCompleted)
                ps.setTimestamp(7, timestamp(g.createdAt))
                ps.setTimestamp(8, timestamp(g.updatedAt))
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun pushInvestments(conn: Connection, rows: List<Investment>) {
        val sql = """
            INSERT INTO investments
                (id, name, type, buy_in_amount, current_value, notes, updated_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                type = EXCLUDED.type,
                buy_in_amount = EXCLUDED.buy_in_amount,
                current_value = EXCLUDED.current_value,
                notes = EXCLUDED.notes,
                updated_at = EXCLUDED.updated_at
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            rows.forEach { i ->
                ps.setInt(1, i.id)
                ps.setString(2, i.name)
                ps.setString(3, i.type)
                ps.setInt(4, i.buyInAmount)
                ps.setInt(5, i.currentValue)
                ps.setString(6, i.notes)
                ps.setTimestamp(7, timestamp(i.updatedAt))
                ps.setTimestamp(8, timestamp(i.createdAt))
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun pushDebts(conn: Connection, rows: List<Debt>) {
        val sql = """
            INSERT INTO debts
                (id, direction, person, amount, description, is_settled, due_date, created_at, updated_at)
            VALUES (?, ?::debt_direction, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                direction = EXCLUDED.direction,
                person = EXCLUDED.person,
                amount = EXCLUDED.amount,
                description = EXCLUDED.description,
                is_settled = EXCLUDED.is_settled,
                due_date = EXCLUDED.due_date,
                updated_at = EXCLUDED.updated_at
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            rows.forEach { d ->
                ps.setInt(1, d.id)
                ps.setString(2, d.direction.name)
                ps.setString(3, d.person)
                ps.setInt(4, d.amount)
                ps.setString(5, d.description)
                ps.setBoolean(6, d.isSettled)
                if (d.dueDate != null) ps.setDate(7, sqlDate(d.dueDate)) else ps.setNull(7, java.sql.Types.DATE)
                ps.setTimestamp(8, timestamp(d.createdAt))
                ps.setTimestamp(9, timestamp(d.updatedAt))
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun pushSnapshots(conn: Connection, rows: List<MonthlySnapshot>) {
        val sql = """
            INSERT INTO monthly_snapshots (id, year, month, kind, amount, recorded_at)
            VALUES (?, ?, ?, ?::snapshot_kind, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                year = EXCLUDED.year,
                month = EXCLUDED.month,
                kind = EXCLUDED.kind,
                amount = EXCLUDED.amount,
                recorded_at = EXCLUDED.recorded_at
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            rows.forEach { s ->
                ps.setInt(1, s.id)
                ps.setInt(2, s.year)
                ps.setInt(3, s.month)
                ps.setString(4, s.kind.name)
                ps.setInt(5, s.amount)
                ps.setTimestamp(6, timestamp(s.recordedAt))
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    // ── helpers ------------------------------------------

    private fun timestamp(epochMs: Long): Timestamp =
        Timestamp(Instant.ofEpochMilli(epochMs).toEpochMilli())

    private fun sqlDate(epochMs: Long): Date {
        val local = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        return Date.valueOf(local.toString())
    }

    private fun lastSyncedAt(table: String): Long = prefs.getLong("last_$table", 0L)

    private fun setLastSyncedAt(table: String, value: Long) {
        prefs.edit().putLong("last_$table", value).apply()
    }

    private fun categorize(e: SQLException): SyncOutcome {
        val state = e.sqlState.orEmpty()
        return when {
            state.startsWith("08") -> {
                Log.w(TAG, "Network/connection error ($state)", e)
                SyncOutcome.TransientFailure("connection")
            }
            state.startsWith("23") -> {
                Log.e(TAG, "Integrity error ($state)", e)
                SyncOutcome.PermanentFailure("integrity")
            }
            state.startsWith("42") -> {
                Log.e(TAG, "Schema mismatch ($state)", e)
                SyncOutcome.PermanentFailure("schema-drift")
            }
            else -> {
                Log.e(TAG, "Unclassified SQL error ($state)", e)
                SyncOutcome.TransientFailure(state.ifEmpty { "unknown" })
            }
        }
    }

    companion object {
        private const val TAG = "MetadataSync"
    }
}
