package com.personal.expensetracker.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.expensetracker.data.local.dao.*
import com.personal.expensetracker.data.local.entity.*

@Database(
    entities = [
        Category::class, Account::class, SmsSource::class,
        Transaction::class, Budget::class, SavingsGoal::class,
        Investment::class, Debt::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun smsSourceDao(): SmsSourceDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun investmentDao(): InvestmentDao
    abstract fun debtDao(): DebtDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker.db"
                )
                    .addCallback(SeedCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Seed default categories
            val categories = listOf(
                // Expense
                "('Food & Dining',    '🍔', '#FF6B35', 0)",
                "('Transport',        '🚌', '#4ECDC4', 0)",
                "('Rent & Housing',   '🏠', '#45B7D1', 0)",
                "('Utilities',        '💡', '#96CEB4', 0)",
                "('Shopping',         '🛍', '#DDA0DD', 0)",
                "('Health',           '💊', '#FF6B6B', 0)",
                "('Entertainment',    '🎬', '#C44DFF', 0)",
                "('Education',        '📚', '#4A90D9', 0)",
                "('Airtime & Data',   '📱', '#F7DC6F', 0)",
                "('Transfers Out',    '📤', '#E67E22', 0)",
                "('Other Expense',    '📦', '#BDC3C7', 0)",
                // Income
                "('Salary',           '💰', '#2ECC71', 1)",
                "('Freelance',        '💻', '#1ABC9C', 1)",
                "('Received',         '📥', '#3498DB', 1)",
                "('Interest',         '🏦', '#27AE60', 1)",
                "('Other Income',     '💵', '#82E0AA', 1)",
            )
            categories.forEach { values ->
                db.execSQL(
                    "INSERT INTO categories (name, icon, color, is_income) VALUES $values"
                )
            }

            // Seed default M-Pesa source
            db.execSQL(
                "INSERT INTO sms_sources (sender, label, is_active) VALUES ('MPESA', 'M-Pesa', 1)"
            )
        }
    }
}
