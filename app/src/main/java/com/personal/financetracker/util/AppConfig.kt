package com.personal.financetracker.util

object AppConfig {
    const val SYNC_INTERVAL_MINUTES = 15L
    const val DIGEST_HOUR = 20
    const val OVERLAY_TIMEOUT_SECONDS = 30L
    const val DEDUP_BUCKET_MILLIS = 60_000L
    const val BUDGET_ALERT_THRESHOLD = 0.9

    /**
     * After successful sync, drop local transactions older than this many
     * days. They're still in Neon — keeping them locally only burns phone
     * storage. Increase if you want longer offline access to history.
     */
    const val LOCAL_RETENTION_DAYS = 7L
}
