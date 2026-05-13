package com.personal.financetracker.parser

import com.personal.financetracker.data.local.entity.TransactionType
import com.personal.financetracker.domain.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests against the actual SMS bodies the user receives in production.
 * If you see a parser miss in real use, add the SMS here as a regression
 * test before tweaking the regex.
 */
class SmsParserTest {

    // ── M-Pesa: sending money ──────────────────────────

    @Test
    fun mpesa_sending_to_person() {
        val body = "UE7NV3RMT8 Confirmed. Ksh50.00 sent to Florence  Mulwa on 7/5/26 at 8:00 PM. " +
            "New M-PESA balance is Ksh18.34. Transaction cost, Ksh0.00. " +
            "Amount you can transact within the day is 499,140.00."
        val parsed = SmsParser.parse("MPESA", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(5_000, parsed.amount)
        assertEquals("UE7NV3RMT8", parsed.reference)
        assertEquals("Florence Mulwa", parsed.counterparty) // collapses double-space
    }

    @Test
    fun mpesa_sending_to_safaricom_offers() {
        // Buying minutes via Safaricom Offers — same shape as a regular send.
        val body = "UEANV430B7 Confirmed. Ksh20.00 sent to Safaricom Offers for account Tunukiwa on " +
            "10/5/26 at 3:48 PM New M-PESA balance is Ksh0.00. Transaction cost, Ksh0.00."
        val parsed = SmsParser.parse("MPESA", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(2_000, parsed.amount)
        assertEquals("UEANV430B7", parsed.reference)
        assertTrue(parsed.counterparty!!.startsWith("Safaricom Offers"))
    }

    @Test
    fun mpesa_sending_to_ziidi() {
        // Automatic Ziidi investment sweep — should still be captured.
        val body = "UE6NV3N0QH Confirmed. Ksh20.00 sent to ZIIDI on 6/5/26 at 7:23 PM " +
            "New M-PESA balance is Ksh873.05."
        val parsed = SmsParser.parse("MPESA", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(2_000, parsed.amount)
        assertEquals("ZIIDI", parsed.counterparty)
    }

    // ── M-Pesa: receiving ──────────────────────────────

    @Test
    fun mpesa_receiving() {
        val body = "UE78T3GS1I Confirmed.You have received Ksh200.00 from CECILIA  NYAMAI 0140***619 " +
            "on 7/5/26 at 4:05 PM  New M-PESA balance is Ksh200.00."
        val parsed = SmsParser.parse("MPESA", body)!!
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(20_000, parsed.amount)
        assertEquals("UE78T3GS1I", parsed.reference)
        assertEquals("CECILIA NYAMAI 0140***619", parsed.counterparty)
    }

    // ── M-Pesa: airtime ────────────────────────────────

    @Test
    fun mpesa_airtime_purchase() {
        val body = "UEDNV4E9A9 confirmed.You bought Ksh10.00 of airtime on 13/5/26 at 11:47 AM." +
            "New M-PESA balance is Ksh0.00."
        val parsed = SmsParser.parse("MPESA", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(1_000, parsed.amount)
        assertEquals("Airtime", parsed.counterparty)
    }

    // ── M-Pesa: Fuliza must be ignored ─────────────────

    @Test
    fun mpesa_fuliza_credit_line_is_skipped() {
        val body = "UEDNV4E9A9 Confirmed. Fuliza M-PESA amount is Ksh 10.00. Access Fee charged Ksh 0.10. " +
            "Total Fuliza M-PESA outstanding amount is Ksh718.48 due on 09/06/26."
        assertNull(SmsParser.parse("MPESA", body))
    }

    @Test
    fun mpesa_fuliza_outstanding_only_is_skipped() {
        val body = "Total Fuliza M-PESA outstanding amount is Ksh100.00. Dial *334# to view details."
        assertNull(SmsParser.parse("MPESA", body))
    }

    // ── Airtel Money: sending ──────────────────────────

    @Test
    fun airtel_sending() {
        val body = "B3MB49H4BYN. Ksh 700 sent to Simion Mwita 2547....9422 on 12/05/26 at 03:26 PM. " +
            "Fee: Ksh 11. Bal: Ksh 1282.0. MPESA ID: UECRI3YR0M"
        val parsed = SmsParser.parse("airtelmoney", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(70_000, parsed.amount)
        assertEquals("B3MB49H4BYN", parsed.reference)
        assertEquals("Simion Mwita 2547....9422", parsed.counterparty)
    }

    // ── Airtel Money: bundle ───────────────────────────

    @Test
    fun airtel_bundle_purchase() {
        val body = "07311573441 Confirmed. Bundle purchase successful of Ksh 10 via Airtel Networks Kenya Ltd " +
            "on 13/05/26 at 06:45 AM. Fee: Ksh 0. Bal: Ksh 1272.0."
        val parsed = SmsParser.parse("airtelmoney", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(1_000, parsed.amount)
        assertEquals("07311573441", parsed.reference)
        assertTrue(parsed.counterparty!!.startsWith("Bundle"))
    }

    // ── Airtel Money: airtime ──────────────────────────

    @Test
    fun airtel_airtime_purchase() {
        val body = "82756100481 Successful. Airtime top up for line 78....384 of Ksh 10 is successful. " +
            "25% bonus airtime received. Bal: Ksh 24.0."
        val parsed = SmsParser.parse("airtelmoney", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(1_000, parsed.amount)
        assertEquals("82756100481", parsed.reference)
        assertEquals("Airtime", parsed.counterparty)
    }

    // ── Sender whitelist ───────────────────────────────

    @Test
    fun unknown_sender_returns_null() {
        val body = "UE7NV3RMT8 Confirmed. Ksh50.00 sent to Florence on 7/5/26"
        assertNull(SmsParser.parse("KCBBANK", body))
    }

    @Test
    fun sender_match_is_case_insensitive() {
        val body = "UE7NV3RMT8 Confirmed. Ksh50.00 sent to Foo on 7/5/26 at 8:00 PM"
        assertNotNull(SmsParser.parse("mpesa", body))
        assertNotNull(SmsParser.parse("MPesa", body))
    }

    // ── Fallback parser ─────────────────────────────────
    //
    // These check the robust fallback that fires when no specific pattern
    // matches. The user invariant: type + amount must be captured even
    // when the SMS format is unfamiliar (e.g. Safaricom changes the
    // template, paybill formats we haven't seen).

    @Test
    fun mpesa_unknown_paybill_format_falls_back_to_expense() {
        // Hypothetical future M-Pesa SMS format that none of our specific
        // patterns match. Still has the action verb "paid" and "Ksh X".
        val body = "UEZX9Y8W7V Confirmed. Ksh250.00 was paid for ACC 123456 on 12/5/26. " +
            "Service charge Ksh1.00."
        val parsed = SmsParser.parse("MPESA", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(25_000, parsed.amount)
        assertEquals("UEZX9Y8W7V", parsed.reference)
    }

    @Test
    fun mpesa_unknown_received_format_falls_back_to_income() {
        // Receiving from a paybill or refund — not in our specific list.
        val body = "UEAB1CD2EF Confirmed. You have been credited Ksh 1,000.00 by KCB BANK on 12/5/26."
        val parsed = SmsParser.parse("MPESA", body)!!
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(100_000, parsed.amount)
    }

    @Test
    fun airtel_unknown_format_falls_back() {
        // An Airtel paybill format we haven't catalogued.
        val body = "X9Y8Z7W6V5 Successful. Bill payment Ksh 350 transferred to KPLC POSTPAID. Bal: Ksh 0.0."
        val parsed = SmsParser.parse("airtelmoney", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(35_000, parsed.amount)
    }

    @Test
    fun marketing_sms_without_action_verb_returns_null() {
        // Pure promotional copy with "Ksh X" but no transactional verb.
        // We must NOT parse this as a transaction.
        val body = "TEH4F8K2A1 Save up to Ksh 5,000 with our new offer! Dial *334# now."
        // Note: contains "TEH4F8K2A1" (a code), but no "sent"/"received"/
        // "paid" etc. Specific patterns fail; fallback should also fail.
        assertNull(SmsParser.parse("MPESA", body))
    }
}
