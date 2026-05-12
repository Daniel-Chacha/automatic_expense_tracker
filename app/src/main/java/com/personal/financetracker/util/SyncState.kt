package com.personal.financetracker.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

/**
 * Records and exposes the outcome of the most recent Neon sync attempt.
 * Backed by SharedPreferences so it survives process death.
 */
object SyncState {

    enum class Outcome { NEVER, SUCCESS, TRANSIENT_FAILURE, PERMANENT_FAILURE }

    private const val PREFS = "sync_state"
    private const val KEY_LAST_AT = "last_sync_at"
    private const val KEY_LAST_OUTCOME = "last_sync_outcome"
    private const val KEY_LAST_COUNT = "last_sync_count"
    private const val KEY_LAST_REASON = "last_sync_reason"

    data class Snapshot(
        val lastSyncAt: Long?,
        val outcome: Outcome,
        val lastCount: Int,
        val lastReason: String?
    )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): Snapshot {
        val p = prefs(context)
        val raw = p.getString(KEY_LAST_OUTCOME, null)
        return Snapshot(
            lastSyncAt = p.getLong(KEY_LAST_AT, 0L).takeIf { it > 0L },
            outcome = raw?.let { runCatching { Outcome.valueOf(it) }.getOrNull() } ?: Outcome.NEVER,
            lastCount = p.getInt(KEY_LAST_COUNT, 0),
            lastReason = p.getString(KEY_LAST_REASON, null)
        )
    }

    fun recordSuccess(context: Context, pushedCount: Int) {
        prefs(context).edit()
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .putString(KEY_LAST_OUTCOME, Outcome.SUCCESS.name)
            .putInt(KEY_LAST_COUNT, pushedCount)
            .remove(KEY_LAST_REASON)
            .apply()
    }

    fun recordTransientFailure(context: Context, reason: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .putString(KEY_LAST_OUTCOME, Outcome.TRANSIENT_FAILURE.name)
            .putString(KEY_LAST_REASON, reason)
            .apply()
    }

    fun recordPermanentFailure(context: Context, reason: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .putString(KEY_LAST_OUTCOME, Outcome.PERMANENT_FAILURE.name)
            .putString(KEY_LAST_REASON, reason)
            .apply()
    }

    /** Flow that emits whenever any sync_state pref changes. */
    fun observe(context: Context): Flow<Snapshot> = callbackFlow {
        val p = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read(context))
        }
        p.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }
        .onStart { emit(read(context)) }
        .distinctUntilChanged()
}
