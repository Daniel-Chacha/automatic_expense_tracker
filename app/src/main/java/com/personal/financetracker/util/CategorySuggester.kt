package com.personal.financetracker.util

import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.domain.categorization.SpecialCounterpartyRules

/**
 * Returns a category id for a counterparty, or null if we cannot confidently
 * decide. "Confident" has two flavours, in priority order:
 *
 * 1. **Special rule** — a hard-coded mapping in [SpecialCounterpartyRules]
 *    (e.g. ZIIDI → Investments). Applies even on the first encounter, so a
 *    well-known counterparty never has to ask the user.
 *
 * 2. **History** — the most-common past category the user assigned to this
 *    counterparty. A single prior assignment is enough; the SmsReceiver
 *    treats any non-null result here as "we know what to do, skip the
 *    overlay".
 */
object CategorySuggester {

    suspend fun suggest(counterparty: String?, db: AppDatabase): Int? {
        if (counterparty.isNullOrBlank()) return null

        // 1. Special rule first — handles the "first ZIIDI transaction"
        //    and similar well-known cases.
        SpecialCounterpartyRules.categoryFor(counterparty)?.let { name ->
            db.categoryDao().findByName(name)?.let { return it.id }
        }

        // 2. History-based suggester.
        return db.transactionDao().getSuggestedCategory(counterparty)
    }
}
