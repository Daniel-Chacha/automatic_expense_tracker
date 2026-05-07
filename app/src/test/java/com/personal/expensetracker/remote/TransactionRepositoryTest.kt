package com.personal.expensetracker.remote

import com.personal.expensetracker.BuildConfig
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.dao.TransactionDao
import com.personal.expensetracker.data.remote.TransactionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

class TransactionRepositoryTest {

    @Test
    fun reports_permanent_failure_when_jdbc_url_missing() = runTest {
        Assume.assumeTrue(
            "NEON_JDBC_URL is set in this build; this test only runs in unconfigured CI",
            BuildConfig.NEON_JDBC_URL.isBlank()
        )
        val db = mockk<AppDatabase>()
        val outcome = TransactionRepository(db).syncTransactions()
        assertTrue(outcome is TransactionRepository.SyncOutcome.PermanentFailure)
    }

    @Test
    fun returns_success_zero_when_no_unsynced_rows() = runTest {
        Assume.assumeTrue(
            "skipped: requires NEON_JDBC_URL to bypass the configured-check",
            BuildConfig.NEON_JDBC_URL.isNotBlank()
        )
        val db = mockk<AppDatabase>()
        val dao = mockk<TransactionDao>()
        every { db.transactionDao() } returns dao
        coEvery { dao.getUnsynced() } returns emptyList()

        val outcome = TransactionRepository(db).syncTransactions()
        assertTrue(outcome is TransactionRepository.SyncOutcome.Success)
        assertEquals(0, (outcome as TransactionRepository.SyncOutcome.Success).count)
    }
}

