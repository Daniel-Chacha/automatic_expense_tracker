package com.personal.financetracker.data.remote

import com.personal.financetracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

/**
 * Neon Postgres client over plain JDBC.
 *
 * Personal sideloaded app: a single-process Android client opens a fresh
 * connection per sync run. We deliberately avoid a connection pool — the
 * device only sees one user, and pooling adds APK weight without payoff.
 */
object NeonClient {

    init {
        // Force the driver to register before DriverManager.getConnection runs
        // on a coroutine thread that may not have triggered its static init.
        Class.forName("org.postgresql.Driver")
    }

    val isConfigured: Boolean
        get() = BuildConfig.NEON_JDBC_URL.isNotBlank()

    /**
     * Acquire a connection, run [block] on Dispatchers.IO, close in a finally.
     * Use this for every JDBC interaction — never hold a connection across calls.
     */
    suspend fun <T> withConnection(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        check(isConfigured) {
            "NEON_JDBC_URL is empty. Set it in local.properties (see local.properties.example)."
        }
        DriverManager.getConnection(BuildConfig.NEON_JDBC_URL, Properties()).use { conn ->
            block(conn)
        }
    }
}
