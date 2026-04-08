package com.personal.expensetracker.util

import com.personal.expensetracker.data.local.AppDatabase

/**
 * Suggests a category based on transaction history.
 * Looks up past transactions with the same counterparty
 * and returns the most commonly used category.
 */
object CategorySuggester {

    /**
     * Returns a suggested category ID for the given counterparty,
     * or null if no history exists.
     */
    suspend fun suggest(counterparty: String?, db: AppDatabase): Int? {
        if (counterparty.isNullOrBlank()) return null
        return db.transactionDao().getSuggestedCategory(counterparty)
    }
}
