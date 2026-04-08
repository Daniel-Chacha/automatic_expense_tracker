package com.personal.expensetracker.data.local.dao

import androidx.room.*
import com.personal.expensetracker.data.local.entity.*
import kotlinx.coroutines.flow.Flow

// ── Categories ─────────────────────────────────────────

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name")
    fun getAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE is_income = :isIncome ORDER BY name")
    fun getByType(isIncome: Boolean): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Int): Category?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Delete
    suspend fun delete(category: Category)
}

// ── Accounts ───────────────────────────────────────────

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name")
    fun getAll(): Flow<List<Account>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Query("UPDATE accounts SET balance = :balance WHERE id = :id")
    suspend fun updateBalance(id: Int, balance: Int)

    @Delete
    suspend fun delete(account: Account)
}

// ── SMS Sources ────────────────────────────────────────

@Dao
interface SmsSourceDao {
    @Query("SELECT * FROM sms_sources WHERE is_active = 1 ORDER BY sender")
    fun getActive(): Flow<List<SmsSource>>

    @Query("SELECT * FROM sms_sources ORDER BY sender")
    fun getAll(): Flow<List<SmsSource>>

    @Query("SELECT * FROM sms_sources WHERE sender = :sender AND is_active = 1 LIMIT 1")
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

// ── Transactions ───────────────────────────────────────

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY transacted_at DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("""
        SELECT * FROM transactions 
        WHERE transacted_at BETWEEN :start AND :end 
        ORDER BY transacted_at DESC
    """)
    fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE category_id = :categoryId ORDER BY transacted_at DESC")
    fun getByCategory(categoryId: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE status = 'UNCATEGORIZED' ORDER BY transacted_at DESC")
    fun getUncategorized(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE is_synced = 0")
    suspend fun getUnsynced(): List<Transaction>

    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE type = 'INCOME' AND transacted_at BETWEEN :start AND :end
    """)
    fun getTotalIncome(start: Long, end: Long): Flow<Int?>

    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE type = 'EXPENSE' AND transacted_at BETWEEN :start AND :end
    """)
    fun getTotalExpense(start: Long, end: Long): Flow<Int?>

    @Query("""
        SELECT c.name, c.color, SUM(t.amount) as total
        FROM transactions t
        JOIN categories c ON t.category_id = c.id
        WHERE t.type = 'EXPENSE' AND t.transacted_at BETWEEN :start AND :end
        GROUP BY c.id
        ORDER BY total DESC
    """)
    fun getExpenseByCategory(start: Long, end: Long): Flow<List<CategoryExpense>>

    @Query("SELECT * FROM transactions ORDER BY transacted_at DESC LIMIT :limit")
    fun getRecent(limit: Int = 10): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Query("UPDATE transactions SET is_synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Int)

    @Query("""
        SELECT category_id as categoryId, SUM(amount) as total 
        FROM transactions 
        WHERE type = 'EXPENSE' AND transacted_at BETWEEN :start AND :end AND category_id IS NOT NULL
        GROUP BY category_id
    """)
    fun getSpentByCategory(start: Long, end: Long): Flow<List<CategorySpent>>

    @Query("""
        SELECT category_id FROM transactions 
        WHERE meta LIKE '%' || :counterparty || '%' AND category_id IS NOT NULL
        GROUP BY category_id ORDER BY COUNT(*) DESC LIMIT 1
    """)
    suspend fun getSuggestedCategory(counterparty: String): Int?

    @Delete
    suspend fun delete(transaction: Transaction)
}

data class CategorySpent(val categoryId: Int, val total: Int)

// ── Budgets ────────────────────────────────────────────

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE month = :month ORDER BY category_id")
    fun getByMonth(month: Long): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE category_id = :categoryId AND month = :month LIMIT 1")
    suspend fun getByCategoryAndMonth(categoryId: Int, month: Long): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Delete
    suspend fun delete(budget: Budget)
}

// ── Savings Goals ──────────────────────────────────────

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals WHERE is_completed = 0 ORDER BY deadline")
    fun getActive(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goals ORDER BY created_at DESC")
    fun getAll(): Flow<List<SavingsGoal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SavingsGoal): Long

    @Update
    suspend fun update(goal: SavingsGoal)

    @Delete
    suspend fun delete(goal: SavingsGoal)
}

// ── Investments ────────────────────────────────────────

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM investments ORDER BY name")
    fun getAll(): Flow<List<Investment>>

    @Query("SELECT SUM(current_value) FROM investments")
    fun getTotalValue(): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(investment: Investment): Long

    @Update
    suspend fun update(investment: Investment)

    @Delete
    suspend fun delete(investment: Investment)
}

// ── Debts ──────────────────────────────────────────────

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts WHERE is_settled = 0 ORDER BY due_date")
    fun getActive(): Flow<List<Debt>>

    @Query("SELECT * FROM debts ORDER BY created_at DESC")
    fun getAll(): Flow<List<Debt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: Debt): Long

    @Update
    suspend fun update(debt: Debt)

    @Query("UPDATE debts SET is_settled = 1 WHERE id = :id")
    suspend fun settle(id: Int)

    @Delete
    suspend fun delete(debt: Debt)
}
