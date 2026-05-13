package com.personal.financetracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Going forward, every schema change MUST be expressed as an explicit Migration here.
// Do not re-introduce fallbackToDestructiveMigration() in AppDatabase — it would silently
// wipe local user data on any schema change.

object Migrations {

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Soft-delete + updated_at columns on every syncable table.
            db.execSQL("ALTER TABLE categories ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE categories ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")

            db.execSQL("ALTER TABLE transactions ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE transactions ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")

            db.execSQL("ALTER TABLE budgets ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE budgets ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")

            db.execSQL("ALTER TABLE savings_goals ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE savings_goals ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")

            db.execSQL("ALTER TABLE investments ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")

            db.execSQL("ALTER TABLE debts ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE debts ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")

            // Monthly snapshots — one row per (year, month, kind) capturing
            // end-of-month totals for savings and investments. Populated by
            // MonthlySnapshotWorker; never deleted by the app.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS monthly_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    year INTEGER NOT NULL,
                    month INTEGER NOT NULL,
                    kind TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    recorded_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_monthly_snapshots_year_month_kind " +
                    "ON monthly_snapshots(year, month, kind)"
            )
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pending_deletions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    table_name TEXT NOT NULL,
                    row_id INTEGER NOT NULL,
                    queued_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_pending_deletions_table_name_row_id " +
                    "ON pending_deletions(table_name, row_id)"
            )
        }
    }
}
