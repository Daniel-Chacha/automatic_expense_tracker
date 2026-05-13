package com.personal.financetracker.domain.parser

import com.personal.financetracker.data.local.entity.TransactionType

data class ParsedTransaction(
    val type: TransactionType,
    val amount: Int,          // cents (KES × 100)
    val reference: String?,
    val counterparty: String?,
    val rawSms: String
)

/**
 * Parses transaction SMS messages from M-Pesa and Airtel Money.
 *
 * Balance is deliberately NOT parsed — Fuliza distorts the SMS "balance"
 * number (it shows your overdraft as if it were yours), so we never trust
 * it for anything in the app.
 */
object SmsParser {

    // ── Common helpers ─────────────────────────────────

    /**
     * The reference code appears at the very start of every supported SMS.
     * M-Pesa uses uppercase alphanumeric (e.g. "UE7NV3RMT8"); Airtel uses
     * uppercase alphanumeric ("B3MB49H4BYN") OR pure numeric ("07311573441").
     * We accept either and stop at the first whitespace or period.
     */
    private val LEADING_REF = Regex("""^([A-Z0-9]{6,})\.?\s""")

    // M-Pesa Fuliza notification — fires alongside a real M-Pesa SMS when
    // you spend money you don't have. We ignore it so the same transaction
    // isn't recorded twice. The real transaction SMS is parsed normally.
    private val MPESA_FULIZA_TAG = Regex(
        """Fuliza\s+M-PESA\s+(?:amount|outstanding)""",
        RegexOption.IGNORE_CASE
    )

    // ── M-Pesa Patterns ────────────────────────────────

    private val MPESA_RECEIVED = Regex(
        """You have received\s+Ksh\s*([\d,]+(?:\.\d+)?)\s+from\s+(.+?)\s+on\b""",
        RegexOption.IGNORE_CASE
    )

    private val MPESA_SENT = Regex(
        """Ksh\s*([\d,]+(?:\.\d+)?)\s+sent to\s+(.+?)\s+on\b""",
        RegexOption.IGNORE_CASE
    )

    private val MPESA_PAID = Regex(
        """Ksh\s*([\d,]+(?:\.\d+)?)\s+paid to\s+(.+?)(?:\s+on\b|\.\s)""",
        RegexOption.IGNORE_CASE
    )

    private val MPESA_AIRTIME = Regex(
        """bought\s+Ksh\s*([\d,]+(?:\.\d+)?)\s+of\s+airtime""",
        RegexOption.IGNORE_CASE
    )

    private val MPESA_WITHDRAW = Regex(
        """withdraw\s+Ksh\s*([\d,]+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE
    )

    // ── Airtel Money Patterns ─────────────────────────

    // Counterparty terminator: every supported Airtel SMS ends the counterparty
    // with " on <date>". Don't include "." here — phone numbers like
    // "2547....9422" contain dots and would be truncated.
    private val AIRTEL_RECEIVED = Regex(
        """received\s+Ksh\.?\s*([\d,]+(?:\.\d+)?)\s+from\s+(.+?)\s+on\b""",
        RegexOption.IGNORE_CASE
    )

    private val AIRTEL_SENT = Regex(
        """Ksh\.?\s*([\d,]+(?:\.\d+)?)\s+sent\s+to\s+(.+?)\s+on\b""",
        RegexOption.IGNORE_CASE
    )

    private val AIRTEL_PAID = Regex(
        """paid\s+Ksh\.?\s*([\d,]+(?:\.\d+)?)\s+to\s+(.+?)\s+on\b""",
        RegexOption.IGNORE_CASE
    )

    private val AIRTEL_BUNDLE = Regex(
        """Bundle\s+purchase\s+successful\s+of\s+Ksh\.?\s*([\d,]+(?:\.\d+)?)\s+via\s+(.+?)\s+on\b""",
        RegexOption.IGNORE_CASE
    )

    private val AIRTEL_AIRTIME = Regex(
        """Airtime\s+top\s+up\s+for\s+line\s+(\S+)\s+of\s+Ksh\.?\s*([\d,]+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE
    )

    private val AIRTEL_WITHDRAW = Regex(
        """withdrawn?\s+Ksh\.?\s*([\d,]+(?:\.\d+)?)(?:\s+from\s+(.+?))?\s+on\b""",
        RegexOption.IGNORE_CASE
    )

    // ── Generic fallback patterns ─────────────────────
    //
    // If none of the specific patterns above match (Safaricom changes the
    // template, an unseen paybill/till format, etc.), these are the bare
    // minimum the parser must capture: direction + amount. Counterparty
    // is best-effort; if not captured we substitute "Unknown" and the
    // user can fill it in via the detail screen.
    //
    // To avoid catching promotional/marketing SMS that mention "Ksh X off",
    // both regexes still require an action verb to be present in the
    // message body — but the verb need not be adjacent to the amount.

    private val ANY_KSH_AMOUNT = Regex(
        """Ksh\.?\s*([\d,]+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE
    )

    private val INCOME_VERB = Regex(
        """\b(received|credited)\b""", RegexOption.IGNORE_CASE
    )

    private val EXPENSE_VERB = Regex(
        """\b(sent|paid|withdrew|withdrawn|withdraw|bought|transferred|debited|purchased)\b""",
        RegexOption.IGNORE_CASE
    )

    private val COUNTERPARTY_FROM = Regex(
        """\bfrom\s+(.+?)(?:\s+on\b|\.|,|$)""", RegexOption.IGNORE_CASE
    )

    private val COUNTERPARTY_TO = Regex(
        """\bto\s+(.+?)(?:\s+on\b|\.|,|$)""", RegexOption.IGNORE_CASE
    )

    // ── Public API ─────────────────────────────────────

    fun parse(sender: String, body: String): ParsedTransaction? {
        // Skip Fuliza credit-line notifications — the matching real transaction
        // SMS is parsed normally and represents the same event.
        if (MPESA_FULIZA_TAG.containsMatchIn(body)) return null

        val ref = LEADING_REF.find(body)?.groupValues?.get(1)
        return when {
            sender.contains("MPESA", ignoreCase = true) ->
                parseMpesa(body, ref) ?: parseGeneric(body, ref)
            sender.contains("AIRTEL", ignoreCase = true) ->
                parseAirtelmoney(body, ref) ?: parseGeneric(body, ref)
            else -> null
        }
    }

    /**
     * Last-resort parser used when no specific pattern matched. Decides
     * direction from a verb in the body and grabs the first Ksh amount.
     * Counterparty is best-effort and falls back to "Unknown".
     *
     * Order of decision: income-verb beats expense-verb if both are
     * present (rare; e.g. "received Ksh X from Y, paid Z" — we treat it
     * as income, the dominant action). For our actual SMS samples one
     * verb is always overwhelmingly the right one.
     */
    private fun parseGeneric(body: String, ref: String?): ParsedTransaction? {
        val amountMatch = ANY_KSH_AMOUNT.find(body) ?: return null
        val amount = parseAmount(amountMatch.groupValues[1])

        return when {
            INCOME_VERB.containsMatchIn(body) -> ParsedTransaction(
                type = TransactionType.INCOME,
                amount = amount,
                reference = ref,
                counterparty = COUNTERPARTY_FROM.find(body)?.groupValues?.get(1)
                    ?.cleanName()?.ifBlank { null } ?: "Unknown",
                rawSms = body
            )
            EXPENSE_VERB.containsMatchIn(body) -> ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = amount,
                reference = ref,
                counterparty = COUNTERPARTY_TO.find(body)?.groupValues?.get(1)
                    ?.cleanName()?.ifBlank { null } ?: "Unknown",
                rawSms = body
            )
            else -> null
        }
    }

    // ── M-Pesa Parser ──────────────────────────────────

    private fun parseMpesa(body: String, ref: String?): ParsedTransaction? {
        // Order matters — match the most specific pattern first.
        MPESA_RECEIVED.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.INCOME,
                amount = parseAmount(match.groupValues[1]),
                reference = ref,
                counterparty = match.groupValues[2].cleanName(),
                rawSms = body
            )
        }

        MPESA_AIRTIME.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[1]),
                reference = ref,
                counterparty = "Airtime",
                rawSms = body
            )
        }

        MPESA_SENT.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[1]),
                reference = ref,
                counterparty = match.groupValues[2].cleanName(),
                rawSms = body
            )
        }

        MPESA_PAID.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[1]),
                reference = ref,
                counterparty = match.groupValues[2].cleanName(),
                rawSms = body
            )
        }

        MPESA_WITHDRAW.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[1]),
                reference = ref,
                counterparty = "ATM Withdrawal",
                rawSms = body
            )
        }

        return null
    }

    // ── Airtel Money Parser ───────────────────────────

    private fun parseAirtelmoney(body: String, ref: String?): ParsedTransaction? {
        AIRTEL_RECEIVED.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.INCOME,
                amount = parseAmount(match.groupValues[1]),
                reference = ref,
                counterparty = match.groupValues[2].cleanName(),
                rawSms = body
            )
        }

        AIRTEL_BUNDLE.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[1]),
                reference = ref,
                counterparty = "Bundle (${match.groupValues[2].cleanName()})",
                rawSms = body
            )
        }

        AIRTEL_AIRTIME.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[2]),
                reference = ref,
                counterparty = "Airtime",
                rawSms = body
            )
        }

        AIRTEL_SENT.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[1]),
                reference = ref,
                counterparty = match.groupValues[2].cleanName(),
                rawSms = body
            )
        }

        AIRTEL_PAID.find(body)?.let { match ->
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[1]),
                reference = ref,
                counterparty = match.groupValues[2].cleanName(),
                rawSms = body
            )
        }

        AIRTEL_WITHDRAW.find(body)?.let { match ->
            val agent = match.groupValues.getOrNull(2)?.cleanName().orEmpty()
            return ParsedTransaction(
                type = TransactionType.EXPENSE,
                amount = parseAmount(match.groupValues[1]),
                reference = ref,
                counterparty = if (agent.isNotEmpty()) "Agent $agent" else "Airtel Money Withdrawal",
                rawSms = body
            )
        }

        return null
    }

    // ── Helpers ────────────────────────────────────────

    private fun parseAmount(amountStr: String): Int {
        val cleaned = amountStr.replace(",", "").trim()
        return (cleaned.toDouble() * 100).toInt()
    }

    /** Collapse runs of whitespace and trim — Safaricom often double-spaces names. */
    private fun String.cleanName(): String = trim().replace(Regex("""\s+"""), " ")
}
