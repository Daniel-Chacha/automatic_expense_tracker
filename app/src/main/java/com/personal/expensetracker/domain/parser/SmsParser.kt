package com.personal.expensetracker.domain.parser

import com.personal.expensetracker.data.local.entity.TransactionType

data class ParsedTransaction(
    val type: TransactionType,
    val amount: Int,        // cents
    val reference: String?,
    val balance: Int?,      // cents
    val counterparty: String?,
    val rawSms: String
)

/**
 * Parses transaction SMS messages from various providers.
 * Currently supports: M-Pesa
 * Add more parsers by extending the `parse` function.
 */
object SmsParser {

    // ── M-Pesa Patterns ────────────────────────────────

    private val MPESA_RECEIVED = Regex(
        """([A-Z0-9]+)\s+Confirmed\.\s*You have received Ksh([\d,]+\.?\d*)\s+from\s+(.+?)\s+on""",
        RegexOption.IGNORE_CASE
    )

    private val MPESA_SENT = Regex(
        """([A-Z0-9]+)\s+Confirmed\.\s*Ksh([\d,]+\.?\d*)\s+sent to\s+(.+?)\s+on""",
        RegexOption.IGNORE_CASE
    )

    private val MPESA_PAID = Regex(
        """([A-Z0-9]+)\s+Confirmed\.\s*Ksh([\d,]+\.?\d*)\s+paid to\s+(.+?)\.?\s""",
        RegexOption.IGNORE_CASE
    )

    private val MPESA_WITHDRAW = Regex(
        """([A-Z0-9]+)\s+Confirmed\..*withdraw\s+Ksh([\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )

    private val MPESA_BUY_GOODS = Regex(
        """([A-Z0-9]+)\s+Confirmed\.\s*Ksh([\d,]+\.?\d*)\s+paid to\s+(.+?)\.\s""",
        RegexOption.IGNORE_CASE
    )

    private val BALANCE_PATTERN = Regex(
        """balance\s+is\s+Ksh([\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )

    // ── Public API ─────────────────────────────────────

    fun parse(sender: String, body: String): ParsedTransaction? {
        return when {
            sender.contains("MPESA", ignoreCase = true) -> parseMpesa(body)
            sender.contains("KCB", ignoreCase = true) -> parseKcb(body)
            sender.contains("EQUITY", ignoreCase = true) -> parseEquity(body)
            else -> null
        }
    }

    // ── M-Pesa Parser ──────────────────────────────────

    private fun parseMpesa(body: String): ParsedTransaction? {
        val balance = BALANCE_PATTERN.find(body)?.let {
            parseAmount(it.groupValues[1])
        }

        // Try each pattern in order of specificity
        MPESA_RECEIVED.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.INCOME,
                amount = parseAmount(match.groupValues[2]),
                reference = match.groupValues[1],
                balance = balance,
                counterparty = match.groupValues[3].trim(),
                rawSms = body
            )
        }

        MPESA_SENT.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[2]),
                reference = match.groupValues[1],
                balance = balance,
                counterparty = match.groupValues[3].trim(),
                rawSms = body
            )
        }

        MPESA_PAID.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[2]),
                reference = match.groupValues[1],
                balance = balance,
                counterparty = match.groupValues[3].trim(),
                rawSms = body
            )
        }

        MPESA_WITHDRAW.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[2]),
                reference = match.groupValues[1],
                balance = balance,
                counterparty = "ATM Withdrawal",
                rawSms = body
            )
        }

        return null
    }

    // ── KCB Parser ────────────────────────────────────

    private val KCB_CREDIT = Regex(
        """(?:credited|deposited|received)\D+Ksh\.?\s*([\\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )

    private val KCB_DEBIT = Regex(
        """(?:debited|withdrawn|paid|sent)\D+Ksh\.?\s*([\\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )

    private val KCB_BALANCE = Regex(
        """(?:balance|bal)[:\s]+Ksh\.?\s*([\\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )

    private fun parseKcb(body: String): ParsedTransaction? {
        val balance = KCB_BALANCE.find(body)?.let { parseAmount(it.groupValues[1]) }

        KCB_CREDIT.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.INCOME,
                amount = parseAmount(match.groupValues[1]),
                reference = null,
                balance = balance,
                counterparty = "KCB",
                rawSms = body
            )
        }

        KCB_DEBIT.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[1]),
                reference = null,
                balance = balance,
                counterparty = "KCB",
                rawSms = body
            )
        }

        return null
    }

    // ── Equity Parser ─────────────────────────────────

    private val EQUITY_CREDIT = Regex(
        """(?:received|credited|deposit)\D+Kshs?\.?\s*([\\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )

    private val EQUITY_DEBIT = Regex(
        """(?:withdrawn|debited|paid|sent)\D+Kshs?\.?\s*([\\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )

    private val EQUITY_BALANCE = Regex(
        """(?:balance|bal)[:\s]+Kshs?\.?\s*([\\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )

    private fun parseEquity(body: String): ParsedTransaction? {
        val balance = EQUITY_BALANCE.find(body)?.let { parseAmount(it.groupValues[1]) }

        EQUITY_CREDIT.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.INCOME,
                amount = parseAmount(match.groupValues[1]),
                reference = null,
                balance = balance,
                counterparty = "Equity",
                rawSms = body
            )
        }

        EQUITY_DEBIT.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[1]),
                reference = null,
                balance = balance,
                counterparty = "Equity",
                rawSms = body
            )
        }

        return null
    }

    // ── Helpers ────────────────────────────────────────

    private fun parseAmount(amountStr: String): Int {
        val cleaned = amountStr.replace(",", "")
        return (cleaned.toDouble() * 100).toInt()
    }
}
