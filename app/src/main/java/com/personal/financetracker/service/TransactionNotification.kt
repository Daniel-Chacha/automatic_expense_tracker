package com.personal.financetracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personal.financetracker.MainActivity
import com.personal.financetracker.R
import com.personal.financetracker.data.local.entity.TransactionType
import com.personal.financetracker.util.FormatUtils

/**
 * Posts a notification when an SMS transaction is captured.
 *
 * Two flavours, distinguished by [needsAttention]:
 *
 * - **needsAttention = true** (uncategorized): the user must pick a category.
 *   Tinted **red** (#E74C3C), title prefixed with ⚠, body says "tap to
 *   categorize". The matching SmsReceiver branch also fires the overlay.
 *
 * - **needsAttention = false** (auto-categorized via special rule or
 *   prior history): purely informational. Tinted **teal** (#4ECDC4),
 *   title prefixed with ✓, body says "Auto-categorized as <Category>".
 *   Overlay does NOT fire.
 *
 * Tapping any notification deep-links to the transaction detail screen.
 */
object TransactionNotification {

    private const val CHANNEL_ID = "transactions_captured"
    private const val CHANNEL_NAME = "Transactions captured"
    private const val CHANNEL_DESC =
        "Posted when an M-Pesa / Airtel Money SMS is parsed into a transaction. " +
            "Red = needs your input, teal = auto-handled. Tap to open the transaction."

    /** Constant for [MainActivity] to read from its launch intent. */
    const val EXTRA_TRANSACTION_ID = "transaction_id"

    // ARGB ints — set via NotificationCompat.Builder.setColor.
    private const val COLOR_ATTENTION = 0xFFE74C3C.toInt() // red
    private const val COLOR_AUTO = 0xFF4ECDC4.toInt()      // teal

    fun show(
        context: Context,
        transactionId: Int,
        amount: Int,
        type: TransactionType,
        counterparty: String?,
        needsAttention: Boolean,
        categoryName: String? = null
    ) {
        ensureChannel(context)

        val sign = if (type == TransactionType.INCOME) "+" else "−"
        val statusGlyph = if (needsAttention) "⚠" else "✓"
        val title = "$statusGlyph $sign ${FormatUtils.formatAmount(amount)}"

        val whoOrWhat = counterparty?.takeIf { it.isNotBlank() } ?:
            if (type == TransactionType.INCOME) "Money received" else "Money out"
        val body = when {
            needsAttention -> "$whoOrWhat — tap to categorize"
            categoryName != null -> "$whoOrWhat · Auto-categorized as $categoryName · Tap to review"
            else -> "$whoOrWhat · Auto-categorized · Tap to review"
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TRANSACTION_ID, transactionId)
        }
        val pending = PendingIntent.getActivity(
            context,
            transactionId, // unique request code → each notification opens its own txn
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val color = if (needsAttention) COLOR_ATTENTION else COLOR_AUTO
        val priority = if (needsAttention) NotificationCompat.PRIORITY_HIGH
                       else NotificationCompat.PRIORITY_DEFAULT

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setColor(color)
            .setColorized(true)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(transactionId, notification)
        }
    }

    /** Dismiss the notification for [transactionId] — used after the user
     *  finishes categorizing via the overlay so the notification doesn't
     *  linger pointing at a now-resolved transaction. */
    fun dismiss(context: Context, transactionId: Int) {
        NotificationManagerCompat.from(context).cancel(transactionId)
    }

    private fun ensureChannel(context: Context) {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }
}
