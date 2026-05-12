package com.personal.financetracker.domain.parser

import com.personal.financetracker.data.local.entity.TransactionType

data class ParsedTransaction(
    val type: TransactionType,
    val amount: Int,        // cents
    val reference: String?,
    val balance: Int?,      // cents
    val counterparty: String?,
    val rawSms: String
)

/**
 * Parses transaction SMS messages from supported providers.
 * Supported: M-Pesa, Airtel Money.
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

    private val MPESA_BALANCE = Regex(
        """balance\s+is\s+Ksh([\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )

    // ── Airtel Money Patterns ─────────────────────────

    private val AIRTEL_RECEIVED = Regex(
        """received\s+Ksh\.?\s*([\d,]+\.?\d*)\s+from\s+(.+?)(?:\s+on|\.|,)""",
        RegexOption.IGNORE_CASE
    )

    private val AIRTEL_SENT = Regex(
        """(?:Ksh\.?\s*([\d,]+\.?\d*)\s+sent\s+to\s+(.+?)(?:\s+on|\.|,)|sent\s+Ksh\.?\s*([\d,]+\.?\d*)\s+to\s+(.+?)(?:\s+on|\.|,))""",
        RegexOption.IGNORE_CASE
    )

    private val AIRTEL_PAID = Regex(
        """paid\s+Ksh\.?\s*([\d,]+\.?\d*)\s+to\s+(.+?)(?:\s+on|\.|,)""",
        RegexOption.IGNORE_CASE
    )

    private val AIRTEL_WITHDRAW = Regex(
        """withdrawn?\s+Ksh\.?\s*([\d,]+\.?\d*)(?:\s+from\s+(.+?))?(?:\s+on|\.|,)""",
        RegexOption.IGNORE_CASE
    )

    private val AIRTEL_REF = Regex(
        """(?:Ref|TID|Transaction\s+ID)[:.\s]+([A-Z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    private val AIRTEL_BALANCE = Regex(
        """(?:new\s+balance|balance\s+is)\D+Ksh\.?\s*([\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )

    // ── Public API ─────────────────────────────────────

    fun parse(sender: String, body: String): ParsedTransaction? {
        return when {
            sender.contains("MPESA", ignoreCase = true) -> parseMpesa(body)
            sender.contains("AIRTELMONEY", ignoreCase = true) ||
                sender.contains("AIRTEL MONEY", ignoreCase = true) ||
                sender.contains("AIRTEL", ignoreCase = true) -> parseAirtelmoney(body)
            else -> null
        }
    }

    // ── M-Pesa Parser ──────────────────────────────────

    private fun parseMpesa(body: String): ParsedTransaction? {
        val balance = MPESA_BALANCE.find(body)?.let {
            parseAmount(it.groupValues[1])
        }

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

    // ── Airtel Money Parser ───────────────────────────

    private fun parseAirtelmoney(body: String): ParsedTransaction? {
        val balance = AIRTEL_BALANCE.find(body)?.let { parseAmount(it.groupValues[1]) }
        val reference = AIRTEL_REF.find(body)?.groupValues?.get(1)

        AIRTEL_RECEIVED.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.INCOME,
                amount = parseAmount(match.groupValues[1]),
                reference = reference,
                balance = balance,
                counterparty = match.groupValues[2].trim(),
                rawSms = body
            )
        }

        AIRTEL_SENT.find(body)?.let { match ->
            // The alternation has two arms; pick whichever group captured.
            val amountStr = match.groupValues[1].ifEmpty { match.groupValues[3] }
            val counterparty = match.groupValues[2].ifEmpty { match.groupValues[4] }
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(amountStr),
                reference = reference,
                balance = balance,
                counterparty = counterparty.trim(),
                rawSms = body
            )
        }

        AIRTEL_PAID.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[1]),
                reference = reference,
                balance = balance,
                counterparty = match.groupValues[2].trim(),
                rawSms = body
            )
        }

        AIRTEL_WITHDRAW.find(body)?.let { match ->
            val agent = match.groupValues.getOrNull(2)?.trim().orEmpty()
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[1]),
                reference = reference,
                balance = balance,
                counterparty = if (agent.isNotEmpty()) "Agent $agent" else "Airtel Money Withdrawal",
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
