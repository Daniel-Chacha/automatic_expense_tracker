package com.personal.financetracker.parser

import com.personal.financetracker.data.local.entity.TransactionType
import com.personal.financetracker.domain.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    // ── M-Pesa ─────────────────────────────────────────

    @Test
    fun mpesa_received_extracts_amount_and_counterparty() {
        val body = "QGH3X4Y2 Confirmed. You have received Ksh1,500.00 from JOHN DOE 0712345678 on 1/4/26 at 10:00 AM. New M-PESA balance is Ksh10,200.50."
        val parsed = SmsParser.parse("MPESA", body)
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(150_000, parsed.amount)
        assertEquals("QGH3X4Y2", parsed.reference)
        assertEquals(1_020_050, parsed.balance)
        assertTrue(parsed.counterparty!!.contains("JOHN DOE"))
    }

    @Test
    fun mpesa_sent_extracts_expense() {
        val body = "QGH4X5Y3 Confirmed. Ksh2,000.00 sent to JANE SMITH 0723456789 on 1/4/26. New M-PESA balance is Ksh8,200.50."
        val parsed = SmsParser.parse("MPESA", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(200_000, parsed.amount)
        assertEquals("QGH4X5Y3", parsed.reference)
    }

    @Test
    fun mpesa_paid_extracts_merchant() {
        val body = "QGH5Z6Y4 Confirmed. Ksh250.00 paid to NAIVAS SUPERMARKET. New M-PESA balance is Ksh7,950.50."
        val parsed = SmsParser.parse("MPESA", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(25_000, parsed.amount)
    }

    @Test
    fun mpesa_withdraw_uses_atm_label() {
        val body = "QGH6W7Y5 Confirmed. on 1/4/26 you withdraw Ksh500.00 from agent 12345. New M-PESA balance is Ksh7,450.50."
        val parsed = SmsParser.parse("MPESA", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(50_000, parsed.amount)
        assertEquals("ATM Withdrawal", parsed.counterparty)
    }

    @Test
    fun mpesa_amount_with_thousands_separator_parses() {
        val body = "ABC123 Confirmed. You have received Ksh10,000.00 from FOO BAR on 1/4/26"
        val parsed = SmsParser.parse("MPESA", body)!!
        assertEquals(1_000_000, parsed.amount)
    }

    // ── Airtel Money ───────────────────────────────────

    @Test
    fun airtel_received_extracts_income() {
        val body = "You have received Ksh 1,500.00 from JOHN DOE on 01/04/2026. New balance is Ksh 5,500.00. Ref: AIR1234"
        val parsed = SmsParser.parse("AIRTELMONEY", body)
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(150_000, parsed.amount)
        assertEquals("AIR1234", parsed.reference)
        assertEquals(550_000, parsed.balance)
    }

    @Test
    fun airtel_sent_extracts_expense_form_one() {
        val body = "Ksh 800.00 sent to JANE SMITH on 01/04/2026. New balance is Ksh 4,700.00. Ref: AIR2345"
        val parsed = SmsParser.parse("AIRTELMONEY", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(80_000, parsed.amount)
        assertEquals("AIR2345", parsed.reference)
    }

    @Test
    fun airtel_paid_extracts_merchant() {
        val body = "You have paid Ksh 250.00 to NAIVAS LIMITED on 01/04/2026. New balance is Ksh 4,450.00. Ref: AIR3456"
        val parsed = SmsParser.parse("AIRTELMONEY", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(25_000, parsed.amount)
    }

    @Test
    fun airtel_withdraw_uses_agent_label() {
        val body = "You have withdrawn Ksh 1,000.00 from agent JOHN AGENT on 01/04/2026. New balance is Ksh 3,450.00. Ref: AIR4567"
        val parsed = SmsParser.parse("AIRTELMONEY", body)!!
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(100_000, parsed.amount)
        assertTrue(parsed.counterparty!!.contains("JOHN AGENT"))
    }

    @Test
    fun airtel_handles_alternate_sender_spellings() {
        val body = "You have received Ksh 100.00 from FOO BAR on 01/04/2026. Ref: X1"
        assertNotNull(SmsParser.parse("AIRTELMONEY", body))
        assertNotNull(SmsParser.parse("AIRTEL MONEY", body))
        assertNotNull(SmsParser.parse("AIRTEL", body))
    }

    // ── Negative cases ─────────────────────────────────

    @Test
    fun unknown_sender_returns_null() {
        assertNull(SmsParser.parse("VODACOM", "anything"))
        assertNull(SmsParser.parse("KCB", "received Ksh 100"))
        assertNull(SmsParser.parse("EQUITY", "credited Ksh 100"))
    }

    @Test
    fun unparseable_body_returns_null() {
        assertNull(SmsParser.parse("MPESA", "random promotional text"))
    }
}
