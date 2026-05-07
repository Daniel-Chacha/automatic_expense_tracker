package com.personal.expensetracker.util

object AppConfig {
    const val SYNC_INTERVAL_MINUTES = 15L
    const val DIGEST_HOUR = 20
    const val OVERLAY_TIMEOUT_SECONDS = 30L
    const val DEDUP_BUCKET_MILLIS = 60_000L
    const val BUDGET_ALERT_THRESHOLD = 0.9
}
