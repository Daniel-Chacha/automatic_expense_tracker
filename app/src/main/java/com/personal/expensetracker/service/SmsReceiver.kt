package com.personal.expensetracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.entity.Transaction
import com.personal.expensetracker.data.local.entity.TransactionStatus
import com.personal.expensetracker.domain.parser.SmsParser
import com.personal.expensetracker.util.CategorySuggester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens for incoming SMS and auto-creates transactions
 * from whitelisted senders (M-Pesa, banks, etc.).
 * Uses smart category suggestion from history.
 * After saving, launches the overlay popup for categorization.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }

        val db = AppDatabase.getInstance(context)

        CoroutineScope(Dispatchers.IO).launch {
            // Only process whitelisted senders
            val smsSource = db.smsSourceDao().findBySender(sender) ?: return@launch

            // Parse the SMS
            val parsed = SmsParser.parse(sender, body) ?: return@launch

            // Smart category suggestion from history
            val suggestedCategory = CategorySuggester.suggest(parsed.counterparty, db)

            // Build meta JSON
            val meta = buildJsonMeta(parsed.reference, parsed.balance, parsed.counterparty, body)

            // Save transaction (auto-categorized if suggestion found)
            val transaction = Transaction(
                type = parsed.type,
                status = if (suggestedCategory != null) TransactionStatus.CONFIRMED else TransactionStatus.UNCATEGORIZED,
                amount = parsed.amount,
                categoryId = suggestedCategory,
                accountId = smsSource.accountId,
                transactedAt = System.currentTimeMillis(),
                meta = meta
            )
            val transactionId = db.transactionDao().insert(transaction).toInt()

            // Update account balance if available
            parsed.balance?.let { balance ->
                smsSource.accountId?.let { accountId ->
                    db.accountDao().updateBalance(accountId, balance)
                }
            }

            // Launch overlay popup for categorization
            val overlayIntent = Intent(context, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_TRANSACTION_ID, transactionId)
                putExtra(OverlayService.EXTRA_AMOUNT, parsed.amount)
                putExtra(OverlayService.EXTRA_TYPE, parsed.type.name)
                putExtra(OverlayService.EXTRA_COUNTERPARTY, parsed.counterparty)
            }
            context.startService(overlayIntent)
        }
    }

    private fun buildJsonMeta(
        ref: String?,
        balance: Int?,
        counterparty: String?,
        rawSms: String
    ): String {
        return buildString {
            append("{")
            ref?.let { append("\"ref\":\"$it\",") }
            balance?.let { append("\"balance\":$it,") }
            counterparty?.let { append("\"counterparty\":\"${it.replace("\"", "\\\"")}\",") }
            append("\"raw\":\"${rawSms.replace("\"", "\\\"").replace("\n", "\\n")}\"")
            append("}")
        }
    }
}
