package com.personal.financetracker.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.financetracker.data.local.entity.*
import kotlinx.coroutines.flow.Flow

// All read queries filter out soft-deleted rows (is_deleted = 0). Deletes are
// translated into UPDATEs via softDelete*. Hard @Delete is kept only for
// migrations / developer purge.

// ── Categories ─────────────────────────────────────────

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE is_deleted = 0 ORDER BY name")
    fun getAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE is_deleted = 0 AND is_income = :isIncome ORDER BY name")
    fun getByType(isIncome: Boolean): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Int): Category?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE AND is_deleted = 0 LIMIT 1")
    suspend fun findByName(name: String): Category?

    @Query("SELECT COUNT(*) FROM categories WHERE is_deleted = 0")
    suspend fun count(): Int

    @Query("SELECT * FROM categories")
    suspend fun getAllIncludingDeleted(): List<Category>

    @Query("SELECT * FROM categories WHERE updated_at > :since")
    suspend fun getModifiedSince(since: Long): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Query("UPDATE categories SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Int, now: Long = System.currentTimeMillis())

    suspend fun softDelete(category: Category) = softDelete(category.id)

    @Delete
    suspend fun delete(category: Category)
}

// ── SMS Sources ────────────────────────────────────────

@Dao
interface SmsSourceDao {
    @Query("SELECT * FROM sms_sources WHERE is_active = 1 ORDER BY sender")
    fun getActive(): Flow<List<SmsSource>>

    @Query("SELECT * FROM sms_sources ORDER BY sender")
    fun getAll(): Flow<List<SmsSource>>

    // COLLATE NOCASE so we match regardless of how the carrier formats the
    // sender name (e.g. "MPESA" vs "mpesa", "airtelmoney" vs "AIRTELMONEY").
    @Query("SELECT * FROM sms_sources WHERE sender = :sender COLLATE NOCASE AND is_active = 1 LIMIT 1")
    suspend fun findBySender(sender: String): SmsSource?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(smsSource: SmsSource): Long

    @Delete
    suspend fun delete(smsSource: SmsSource)
}

// ── Transaction query result types ─────────────────────

data class CategoryExpense(
    val name: String,
    val color: String?,
    val total: Int
)

data class CategorySpent(val categoryId: Int, val total: Int)

data class MonthAggregate(val month: Int, val total: Int)

data class YearAggregate(val year: Int, val total: Int)

// ── Transactions ───────────────────────────────────────

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY transacted_at DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id AND is_deleted = 0 LIMIT 1")
    fun getByIdFlow(id: Int): Flow<Transaction?>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Transaction?

    @Query("SELECT COUNT(*) FROM transactions WHERE is_deleted = 0")
    suspend fun count(): Int

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0 AND transacted_at BETWEEN :start AND :end
        ORDER BY transacted_at DESC
    """)
    fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 AND category_id = :categoryId ORDER BY transacted_at DESC")
    fun getByCategory(categoryId: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 AND status = 'UNCATEGORIZED' ORDER BY transacted_at DESC")
    fun getUncategorized(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE is_synced = 0")
    suspend fun getUnsynced(): List<Transaction>

    @Query("SELECT COUNT(*) FROM transactions WHERE is_synced = 0")
    fun getUnsyncedCountFlow(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 AND reference = :ref LIMIT 1")
    suspend fun findByReference(ref: String): Transaction?

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 AND dedup_hash = :hash LIMIT 1")
    suspend fun findByDedupHash(hash: String): Transaction?

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY transacted_at DESC")
    fun pagingSource(): PagingSource<Int, Transaction>

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0 AND transacted_at BETWEEN :start AND :end
        ORDER BY transacted_at DESC
    """)
    fun pagingSourceByDateRange(start: Long, end: Long): PagingSource<Int, Transaction>

    @Query("""
        SELECT SUM(amount) FROM transactions
        WHERE is_deleted = 0 AND type = 'INCOME' AND transacted_at BETWEEN :start AND :end
    """)
    fun getTotalIncome(start: Long, end: Long): Flow<Int?>

    @Query("""
        SELECT SUM(amount) FROM transactions
        WHERE is_deleted = 0 AND type = 'EXPENSE' AND transacted_at BETWEEN :start AND :end
    """)
    fun getTotalExpense(start: Long, end: Long): Flow<Int?>

    @Query("""
        SELECT c.name, c.color, SUM(t.amount) as total
        FROM transactions t
        JOIN categories c ON t.category_id = c.id
        WHERE t.is_deleted = 0 AND c.is_deleted = 0
          AND t.type = 'EXPENSE' AND t.transacted_at BETWEEN :start AND :end
        GROUP BY c.id
        ORDER BY total DESC
    """)
    fun getExpenseByCategory(start: Long, end: Long): Flow<List<CategoryExpense>>

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY transacted_at DESC LIMIT :limit")
    fun getRecent(limit: Int = 10): Flow<List<Transaction>>

    // ── Year/month aggregations (for the yearly chart) ──

    /** Per-month totals for the given calendar year. month is 1..12. */
    @Query("""
        SELECT CAST(strftime('%m', datetime(transacted_at/1000, 'unixepoch', 'localtime')) AS INTEGER) as month,
               SUM(amount) as total
        FROM transactions
        WHERE is_deleted = 0 AND type = 'INCOME'
          AND CAST(strftime('%Y', datetime(transacted_at/1000, 'unixepoch', 'localtime')) AS INTEGER) = :year
        GROUP BY month
    """)
    fun getIncomeByMonthForYear(year: Int): Flow<List<MonthAggregate>>

    @Query("""
        SELECT CAST(strftime('%m', datetime(transacted_at/1000, 'unixepoch', 'localtime')) AS INTEGER) as month,
               SUM(amount) as total
        FROM transactions
        WHERE is_deleted = 0 AND type = 'EXPENSE'
          AND CAST(strftime('%Y', datetime(transacted_at/1000, 'unixepoch', 'localtime')) AS INTEGER) = :year
        GROUP BY month
    """)
    fun getExpenseByMonthForYear(year: Int): Flow<List<MonthAggregate>>

    /** All-time, grouped by year. */
    @Query("""
        SELECT CAST(strftime('%Y', datetime(transacted_at/1000, 'unixepoch', 'localtime')) AS INTEGER) as year,
               SUM(amount) as total
        FROM transactions
        WHERE is_deleted = 0 AND type = 'INCOME'
        GROUP BY year ORDER BY year
    """)
    fun getIncomeByYear(): Flow<List<YearAggregate>>

    @Query("""
        SELECT CAST(strftime('%Y', datetime(transacted_at/1000, 'unixepoch', 'localtime')) AS INTEGER) as year,
               SUM(amount) as total
        FROM transactions
        WHERE is_deleted = 0 AND type = 'EXPENSE'
        GROUP BY year ORDER BY year
    """)
    fun getExpenseByYear(): Flow<List<YearAggregate>>

    /** Distinct calendar years that have at least one transaction. */
    @Query("""
        SELECT DISTINCT CAST(strftime('%Y', datetime(transacted_at/1000, 'unixepoch', 'localtime')) AS INTEGER) as year
        FROM transactions WHERE is_deleted = 0 ORDER BY year
    """)
    fun getDistinctYears(): Flow<List<Int>>

    /** Whole-year category donut. */
    @Query("""
        SELECT c.name, c.color, SUM(t.amount) as total
        FROM transactions t
        JOIN categories c ON t.category_id = c.id
        WHERE t.is_deleted = 0 AND c.is_deleted = 0 AND t.type = 'EXPENSE'
          AND CAST(strftime('%Y', datetime(t.transacted_at/1000, 'unixepoch', 'localtime')) AS INTEGER) = :year
        GROUP BY c.id ORDER BY total DESC
    """)
    fun getExpenseByCategoryForYear(year: Int): Flow<List<CategoryExpense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Query("UPDATE transactions SET is_synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Int)

    @Query("""
        SELECT category_id as categoryId, SUM(amount) as total
        FROM transactions
        WHERE is_deleted = 0 AND type = 'EXPENSE' AND transacted_at BETWEEN :start AND :end AND category_id IS NOT NULL
        GROUP BY category_id
    """)
    fun getSpentByCategory(start: Long, end: Long): Flow<List<CategorySpent>>

    @Query("""
        SELECT category_id FROM transactions
        WHERE is_deleted = 0 AND counterparty = :counterparty AND category_id IS NOT NULL
        GROUP BY category_id ORDER BY COUNT(*) DESC LIMIT 1
    """)
    suspend fun getSuggestedCategory(counterparty: String): Int?

    @Query("""
        UPDATE transactions
        SET sync_failures = sync_failures + 1, last_sync_attempt_at = :now
        WHERE id IN (:ids)
    """)
    suspend fun recordSyncFailure(ids: List<Int>, now: Long)

    /**
     * Drop synced transactions older than [cutoff] millis. Returns affected rows.
     * Only deletes rows where is_synced = 1, so pending uploads are preserved.
     * Also only purges already-soft-deleted rows so live data is never lost.
     */
    @Query("""
        DELETE FROM transactions
        WHERE is_synced = 1 AND is_deleted = 1 AND transacted_at < :cutoff
    """)
    suspend fun pruneSyncedBefore(cutoff: Long): Int

    /** Soft-delete: keep the row, mark for sync. */
    @Query("""
        UPDATE transactions
        SET is_deleted = 1, is_synced = 0, updated_at = :now
        WHERE id = :id
    """)
    suspend fun softDelete(id: Int, now: Long = System.currentTimeMillis())

    suspend fun softDelete(transaction: Transaction) = softDelete(transaction.id)

    @Delete
    suspend fun delete(transaction: Transaction)
}

// ── Budgets ────────────────────────────────────────────

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE is_deleted = 0 AND month = :month ORDER BY category_id")
    fun getByMonth(month: Long): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE is_deleted = 0 AND category_id = :categoryId AND month = :month LIMIT 1")
    suspend fun getByCategoryAndMonth(categoryId: Int, month: Long): Budget?

    @Query("SELECT * FROM budgets WHERE is_deleted = 0")
    fun getAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE is_deleted = 0 AND month BETWEEN :start AND :end")
    fun getByMonthRange(start: Long, end: Long): Flow<List<Budget>>

    @Query("SELECT COUNT(*) FROM budgets WHERE is_deleted = 0")
    suspend fun count(): Int

    @Query("SELECT * FROM budgets WHERE updated_at > :since")
    suspend fun getModifiedSince(since: Long): List<Budget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Query("UPDATE budgets SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Int, now: Long = System.currentTimeMillis())

    suspend fun softDelete(budget: Budget) = softDelete(budget.id)

    @Delete
    suspend fun delete(budget: Budget)
}

// ── Savings Goals ──────────────────────────────────────

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals WHERE is_deleted = 0 AND is_completed = 0 ORDER BY deadline")
    fun getActive(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goals WHERE is_deleted = 0 ORDER BY created_at DESC")
    fun getAll(): Flow<List<SavingsGoal>>

    @Query("SELECT IFNULL(SUM(current_amount), 0) FROM savings_goals WHERE is_deleted = 0 AND is_completed = 0")
    suspend fun getCurrentTotal(): Int

    @Query("SELECT COUNT(*) FROM savings_goals WHERE is_deleted = 0")
    suspend fun count(): Int

    @Query("SELECT * FROM savings_goals WHERE updated_at > :since")
    suspend fun getModifiedSince(since: Long): List<SavingsGoal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SavingsGoal): Long

    @Update
    suspend fun update(goal: SavingsGoal)

    @Query("UPDATE savings_goals SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Int, now: Long = System.currentTimeMillis())

    suspend fun softDelete(goal: SavingsGoal) = softDelete(goal.id)

    @Delete
    suspend fun delete(goal: SavingsGoal)
}

// ── Investments ────────────────────────────────────────

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM investments WHERE is_deleted = 0 ORDER BY name")
    fun getAll(): Flow<List<Investment>>

    @Query("SELECT SUM(current_value) FROM investments WHERE is_deleted = 0")
    fun getTotalValue(): Flow<Int?>

    @Query("SELECT IFNULL(SUM(current_value), 0) FROM investments WHERE is_deleted = 0")
    suspend fun getCurrentTotal(): Int

    @Query("SELECT COUNT(*) FROM investments WHERE is_deleted = 0")
    suspend fun count(): Int

    @Query("SELECT * FROM investments WHERE updated_at > :since")
    suspend fun getModifiedSince(since: Long): List<Investment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(investment: Investment): Long

    @Update
    suspend fun update(investment: Investment)

    @Query("UPDATE investments SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Int, now: Long = System.currentTimeMillis())

    suspend fun softDelete(investment: Investment) = softDelete(investment.id)

    @Delete
    suspend fun delete(investment: Investment)
}

// ── Debts ──────────────────────────────────────────────

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts WHERE is_deleted = 0 AND is_settled = 0 ORDER BY due_date")
    fun getActive(): Flow<List<Debt>>

    @Query("SELECT * FROM debts WHERE is_deleted = 0 ORDER BY created_at DESC")
    fun getAll(): Flow<List<Debt>>

    @Query("SELECT COUNT(*) FROM debts WHERE is_deleted = 0")
    suspend fun count(): Int

    @Query("SELECT * FROM debts WHERE updated_at > :since")
    suspend fun getModifiedSince(since: Long): List<Debt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: Debt): Long

    @Update
    suspend fun update(debt: Debt)

    @Query("UPDATE debts SET is_settled = 1, updated_at = :now WHERE id = :id")
    suspend fun settle(id: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE debts SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Int, now: Long = System.currentTimeMillis())

    suspend fun softDelete(debt: Debt) = softDelete(debt.id)

    @Delete
    suspend fun delete(debt: Debt)
}

// ── Monthly Snapshots ──────────────────────────────────

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM monthly_snapshots WHERE year = :year ORDER BY month")
    fun getByYear(year: Int): Flow<List<MonthlySnapshot>>

    @Query("SELECT * FROM monthly_snapshots ORDER BY year, month")
    fun getAll(): Flow<List<MonthlySnapshot>>

    @Query("SELECT DISTINCT year FROM monthly_snapshots ORDER BY year")
    fun getDistinctYears(): Flow<List<Int>>

    @Query("""
        SELECT * FROM monthly_snapshots
        WHERE year = :year AND month = :month AND kind = :kind LIMIT 1
    """)
    suspend fun get(year: Int, month: Int, kind: SnapshotKind): MonthlySnapshot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: MonthlySnapshot): Long

    @Query("SELECT COUNT(*) FROM monthly_snapshots")
    suspend fun count(): Int

    @Query("SELECT * FROM monthly_snapshots WHERE recorded_at > :since")
    suspend fun getModifiedSince(since: Long): List<MonthlySnapshot>
}

// ── Pending Deletions (outbox for hard deletes) ────────

@Dao
interface PendingDeletionDao {
    @Query("SELECT * FROM pending_deletions ORDER BY queued_at")
    suspend fun getAll(): List<PendingDeletion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deletion: PendingDeletion): Long

    @Query("DELETE FROM pending_deletions WHERE id = :id")
    suspend fun remove(id: Int)

    @Query("DELETE FROM pending_deletions WHERE table_name = :table AND row_id = :rowId")
    suspend fun removeByRow(table: String, rowId: Int)
}
