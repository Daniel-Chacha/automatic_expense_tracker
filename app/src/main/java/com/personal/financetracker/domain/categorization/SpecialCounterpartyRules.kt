package com.personal.financetracker.domain.categorization

/**
 * Hard-coded counterparty → category mappings applied BEFORE the
 * history-based [com.personal.financetracker.util.CategorySuggester] runs.
 * Their purpose is to auto-categorize transactions whose counterparty has
 * an obvious, well-known category — even on first encounter, before the
 * user has had a chance to teach the suggester.
 *
 * Add new rules sparingly. Most categorization should come from the user's
 * own history (which the suggester picks up automatically after one
 * manual assignment).
 */
object SpecialCounterpartyRules {

    /**
     * Returns the canonical category NAME a counterparty maps to, or null
     * if no rule applies. Caller is expected to resolve the name to a
     * category id via [com.personal.financetracker.data.local.dao.CategoryDao.findByName].
     */
    fun categoryFor(counterparty: String?): String? {
        if (counterparty.isNullOrBlank()) return null
        val c = counterparty.trim()
        return when {
            // M-Pesa's Ziidi money-market sweep — money leaves M-Pesa but
            // it's an investment, not a true expense.
            c.equals("ZIIDI", ignoreCase = true) -> "Investments"
            // Both M-Pesa airtime ("You bought Ksh… of airtime") and Airtel
            // airtime ("Airtime top up for line …") feed the parser the
            // counterparty "Airtime".
            c.equals("Airtime", ignoreCase = true) -> "Airtime & Data"
            // Airtel data bundles arrive parsed as "Bundle (<provider>)".
            c.startsWith("Bundle", ignoreCase = true) -> "Airtime & Data"
            else -> null
        }
    }
}
