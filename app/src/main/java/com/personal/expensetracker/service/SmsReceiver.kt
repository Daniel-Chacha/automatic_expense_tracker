package com.personal.expensetracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.entity.Transaction
import com.personal.expensetracker.data.local.entity.TransactionMeta
import com.personal.expensetracker.data.local.entity.TransactionStatus
import com.personal.expensetracker.data.local.entity.encode
import com.personal.expensetracker.domain.parser.SmsParser
import com.personal.expensetracker.util.AppConfig
import com.personal.expensetracker.util.CategorySuggester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Listens for incoming SMS from whitelisted senders, parses transactions,
 * and persists them with dedup safeguards. Triggers the categorization overlay.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val now = System.currentTimeMillis()

        val pendingResult = goAsync()
        val db = AppDatabase.getInstance(context)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val smsSource = db.smsSourceDao().findBySender(sender) ?: return@launch
                val parsed = SmsParser.parse(sender, body) ?: return@launch

                // Reference-level dedup (carrier retransmissions, app reinstall replays).
                parsed.reference?.let { ref ->
                    if (db.transactionDao().findByReference(ref) != null) {
                        Log.d(TAG, "Duplicate by reference $ref — skipping")
                        return@launch
                    }
                }

                // Hash-based dedup for messages without a reference, bucketed to the
                // nearest minute so duplicates within the same window collide.
                val dedupHash = sha256Hex("$sender|$body|${now / AppConfig.DEDUP_BUCKET_MILLIS}")
                if (db.transactionDao().findByDedupHash(dedupHash) != null) {
                    Log.d(TAG, "Duplicate by dedup hash — skipping")
                    return@launch
                }

                val suggestedCategory = CategorySuggester.suggest(parsed.counterparty, db)
                val meta = TransactionMeta(
                    ref = parsed.reference,
                    balance = parsed.balance,
                    counterparty = parsed.counterparty,
                    raw = body
                ).encode()

                val transaction = Transaction(
                    type = parsed.type,
                    status = if (suggestedCategory != null) TransactionStatus.CONFIRMED else TransactionStatus.UNCATEGORIZED,
                    amount = parsed.amount,
                    categoryId = suggestedCategory,
                    accountId = smsSource.accountId,
                    transactedAt = now,
                    meta = meta,
                    reference = parsed.reference,
                    counterparty = parsed.counterparty,
                    dedupHash = dedupHash
                )
                val transactionId = db.transactionDao().insert(transaction).toInt()

                parsed.balance?.let { balance ->
                    smsSource.accountId?.let { accountId ->
                        db.accountDao().updateBalance(accountId, balance)
                    }
                }

                val overlayIntent = Intent(context, OverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_TRANSACTION_ID, transactionId)
                    putExtra(OverlayService.EXTRA_AMOUNT, parsed.amount)
                    putExtra(OverlayService.EXTRA_TYPE, parsed.type.name)
                    putExtra(OverlayService.EXTRA_COUNTERPARTY, parsed.counterparty)
                }
                context.startService(overlayIntent)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to process SMS from $sender", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
