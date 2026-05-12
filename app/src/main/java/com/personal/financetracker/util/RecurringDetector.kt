package com.personal.financetracker.util

import com.personal.financetracker.data.local.entity.Transaction
import com.personal.financetracker.data.local.entity.decodeMeta

/**
 * Detects recurring transactions by grouping by counterparty + approximate amount.
 */
object RecurringDetector {

    data class RecurringPattern(
        val counterparty: String,
        val averageAmount: Int,
        val occurrences: Int,
        val lastDate: Long,
        val categoryId: Int?
    )

    fun detect(transactions: List<Transaction>, minOccurrences: Int = 2): List<RecurringPattern> {
        val withCounterparty = transactions.mapNotNull { txn ->
            val cp = txn.counterparty ?: decodeMeta(txn.meta)?.counterparty
            if (cp.isNullOrBlank()) return@mapNotNull null
            Triple(cp, txn.amount, txn)
        }

        // Group by counterparty
        return withCounterparty
            .groupBy { it.first.lowercase() }
            .filter { it.value.size >= minOccurrences }
            .map { (counterparty, group) ->
                val amounts = group.map { it.second }
                val avgAmount = amounts.average().toInt()
                // Only consider recurring if amounts are within 20% of average
                val consistent = amounts.all {
                    kotlin.math.abs(it - avgAmount) < avgAmount * 0.2 || avgAmount == 0
                }
                if (!consistent) return@map null

                RecurringPattern(
                    counterparty = group.first().first,
                    averageAmount = avgAmount,
                    occurrences = group.size,
                    lastDate = group.maxOf { it.third.transactedAt },
                    categoryId = group.mapNotNull { it.third.categoryId }
                        .groupingBy { it }.eachCount()
                        .maxByOrNull { it.value }?.key
                )
            }
            .filterNotNull()
            .sortedByDescending { it.occurrences }
    }
}
