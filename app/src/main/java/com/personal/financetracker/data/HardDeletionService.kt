package com.personal.financetracker.data

import androidx.room.withTransaction
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.*

/**
 * Atomically hard-deletes a row from Room and queues a matching DELETE for
 * Neon (via the `pending_deletions` outbox). The SyncWorker drains the
 * outbox on its next run.
 *
 * Used wherever the UI lets the user permanently destroy a record. The
 * confirmation modal that gates this lives in
 * [com.personal.financetracker.ui.components.DeleteConfirmDialog].
 */
object HardDeletionService {

    suspend fun deleteTransaction(db: AppDatabase, txn: Transaction) {
        db.withTransaction {
            db.pendingDeletionDao().insert(PendingDeletion(tableName = "transactions", rowId = txn.id))
            db.transactionDao().delete(txn)
        }
    }

    suspend fun deleteCategory(db: AppDatabase, category: Category) {
        db.withTransaction {
            db.pendingDeletionDao().insert(PendingDeletion(tableName = "categories", rowId = category.id))
            db.categoryDao().delete(category)
        }
    }

    suspend fun deleteBudget(db: AppDatabase, budget: Budget) {
        db.withTransaction {
            db.pendingDeletionDao().insert(PendingDeletion(tableName = "budgets", rowId = budget.id))
            db.budgetDao().delete(budget)
        }
    }

    suspend fun deleteSavingsGoal(db: AppDatabase, goal: SavingsGoal) {
        db.withTransaction {
            db.pendingDeletionDao().insert(PendingDeletion(tableName = "savings_goals", rowId = goal.id))
            db.savingsGoalDao().delete(goal)
        }
    }

    suspend fun deleteInvestment(db: AppDatabase, investment: Investment) {
        db.withTransaction {
            db.pendingDeletionDao().insert(PendingDeletion(tableName = "investments", rowId = investment.id))
            db.investmentDao().delete(investment)
        }
    }

    suspend fun deleteDebt(db: AppDatabase, debt: Debt) {
        db.withTransaction {
            db.pendingDeletionDao().insert(PendingDeletion(tableName = "debts", rowId = debt.id))
            db.debtDao().delete(debt)
        }
    }
}
