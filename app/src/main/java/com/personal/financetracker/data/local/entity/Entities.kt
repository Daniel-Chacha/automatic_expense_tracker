package com.personal.financetracker.data.local.entity

import androidx.room.*

// ── Enums ──────────────────────────────────────────────

enum class TransactionType { INCOME, EXPENSE }
enum class TransactionStatus { CONFIRMED, UNCATEGORIZED, PENDING_REVIEW }
enum class DebtDirection { LENT, BORROWED }
enum class SnapshotKind { SAVINGS, INVESTMENT }

// ── Entities ───────────────────────────────────────────

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    @ColumnInfo(name = "is_income") val isIncome: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sms_sources")
data class SmsSource(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String,
    val label: String? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("transacted_at"),
        Index("category_id"),
        Index("counterparty"),
        Index("reference"),
        Index(value = ["dedup_hash"], unique = true)
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: TransactionType,
    val status: TransactionStatus = TransactionStatus.UNCATEGORIZED,
    val amount: Int,
    @ColumnInfo(name = "category_id") val categoryId: Int? = null,
    val description: String? = null,
    @ColumnInfo(name = "transacted_at") val transactedAt: Long = System.currentTimeMillis(),
    val meta: String? = null,
    val reference: String? = null,
    val counterparty: String? = null,
    @ColumnInfo(name = "dedup_hash") val dedupHash: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @ColumnInfo(name = "last_sync_attempt_at") val lastSyncAttemptAt: Long? = null,
    @ColumnInfo(name = "sync_failures") val syncFailures: Int = 0,
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "budgets",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["category_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["category_id", "month"], unique = true)]
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "category_id") val categoryId: Int,
    val month: Long,
    val amount: Int,
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    @ColumnInfo(name = "target_amount") val targetAmount: Int,
    @ColumnInfo(name = "current_amount") val currentAmount: Int = 0,
    val deadline: Long? = null,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "investments")
data class Investment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String? = null,
    @ColumnInfo(name = "buy_in_amount") val buyInAmount: Int,
    @ColumnInfo(name = "current_value") val currentValue: Int,
    val notes: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false
)

@Entity(
    tableName = "debts",
    indices = [Index("is_settled")]
)
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val direction: DebtDirection,
    val person: String,
    val amount: Int,
    val description: String? = null,
    @ColumnInfo(name = "is_settled") val isSettled: Boolean = false,
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "monthly_snapshots",
    indices = [Index(value = ["year", "month", "kind"], unique = true)]
)
data class MonthlySnapshot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val year: Int,
    val month: Int,
    val kind: SnapshotKind,
    val amount: Int,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long = System.currentTimeMillis()
)

/**
 * Outbox for hard deletes. When the user confirms a destructive delete in the
 * UI, we hard-delete the row from Room and insert a row here so the next
 * SyncWorker pass can issue the matching DELETE to Neon.
 */
@Entity(
    tableName = "pending_deletions",
    indices = [Index(value = ["table_name", "row_id"], unique = true)]
)
data class PendingDeletion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "row_id") val rowId: Int,
    @ColumnInfo(name = "queued_at") val queuedAt: Long = System.currentTimeMillis()
)
