package com.personal.financetracker.categorization

import com.personal.financetracker.domain.categorization.SpecialCounterpartyRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecialCounterpartyRulesTest {

    @Test
    fun ziidi_maps_to_investments() {
        assertEquals("Investments", SpecialCounterpartyRules.categoryFor("ZIIDI"))
    }

    @Test
    fun ziidi_is_case_insensitive() {
        assertEquals("Investments", SpecialCounterpartyRules.categoryFor("ziidi"))
        assertEquals("Investments", SpecialCounterpartyRules.categoryFor("Ziidi"))
    }

    @Test
    fun airtime_counterparty_maps_to_airtime_and_data() {
        assertEquals("Airtime & Data", SpecialCounterpartyRules.categoryFor("Airtime"))
    }

    @Test
    fun bundle_prefix_maps_to_airtime_and_data() {
        assertEquals(
            "Airtime & Data",
            SpecialCounterpartyRules.categoryFor("Bundle (Airtel Networks Kenya Ltd)")
        )
        assertEquals("Airtime & Data", SpecialCounterpartyRules.categoryFor("Bundle"))
    }

    @Test
    fun unknown_counterparty_returns_null() {
        assertNull(SpecialCounterpartyRules.categoryFor("Florence Mulwa"))
        assertNull(SpecialCounterpartyRules.categoryFor("Naivas Supermarket"))
    }

    @Test
    fun blank_or_null_counterparty_returns_null() {
        assertNull(SpecialCounterpartyRules.categoryFor(null))
        assertNull(SpecialCounterpartyRules.categoryFor(""))
        assertNull(SpecialCounterpartyRules.categoryFor("   "))
    }
}
