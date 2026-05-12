package com.personal.financetracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.financetracker.data.local.dao.*
import com.personal.financetracker.data.local.entity.*

@Database(
    entities = [
        Category::class, Account::class, SmsSource::class,
        Transaction::class, Budget::class, SavingsGoal::class,
        Investment::class, Debt::class
    ],
    version = 3,
    exportSchema = true
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
                    "finance_tracker.db"
                )
                    .addCallback(SeedCallback())
                    // One-time destructive reset for v1→v2; v1 was unreleased.
                    // Future migrations must use addMigrations(...).
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            val now = System.currentTimeMillis()

            // Seed default categories
            val categories = listOf(
                // Expense
                "('Food & Dining',    '\uD83C\uDF54', '#FF6B35', 0, $now)",
                "('Transport',        '\uD83D\uDE8C', '#4ECDC4', 0, $now)",
                "('Rent & Housing',   '\uD83C\uDFE0', '#45B7D1', 0, $now)",
                "('Utilities',        '\uD83D\uDCA1', '#96CEB4', 0, $now)",
                "('Shopping',         '\uD83D\uDECD', '#DDA0DD', 0, $now)",
                "('Health',           '\uD83D\uDC8A', '#FF6B6B', 0, $now)",
                "('Entertainment',    '\uD83C\uDFAC', '#C44DFF', 0, $now)",
                "('Education',        '\uD83D\uDCDA', '#4A90D9', 0, $now)",
                "('Airtime & Data',   '\uD83D\uDCF1', '#F7DC6F', 0, $now)",
                "('Transfers Out',    '\uD83D\uDCE4', '#E67E22', 0, $now)",
                "('Other Expense',    '\uD83D\uDCE6', '#BDC3C7', 0, $now)",
                // Income
                "('Salary',           '\uD83D\uDCB0', '#2ECC71', 1, $now)",
                "('Freelance',        '\uD83D\uDCBB', '#1ABC9C', 1, $now)",
                "('Received',         '\uD83D\uDCE5', '#3498DB', 1, $now)",
                "('Interest',         '\uD83C\uDFE6', '#27AE60', 1, $now)",
                "('Other Income',     '\uD83D\uDCB5', '#82E0AA', 1, $now)",
            )
            categories.forEach { values ->
                db.execSQL(
                    "INSERT INTO categories (name, icon, color, is_income, created_at) VALUES $values"
                )
            }

            // Seed default SMS sources (M-Pesa and Airtel Money)
            db.execSQL(
                "INSERT INTO sms_sources (sender, label, is_active, created_at) VALUES " +
                    "('MPESA', 'M-Pesa', 1, $now), ('AIRTELMONEY', 'Airtel Money', 1, $now)"
            )

            // Partial unique index that Room cannot express — restrict the
            // unique constraint to rows where reference is non-null.
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_txn_reference_time " +
                    "ON transactions(reference, transacted_at) WHERE reference IS NOT NULL"
            )
        }
    }
}
