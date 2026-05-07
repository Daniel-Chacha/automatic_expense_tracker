package com.personal.expensetracker.local

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.entity.Transaction
import com.personal.expensetracker.data.local.entity.TransactionStatus
import com.personal.expensetracker.data.local.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun findByReference_returns_inserted_row() = runBlocking {
        val id = db.transactionDao().insert(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = 1234,
                reference = "ABC123",
                dedupHash = "h1"
            )
        ).toInt()
        val found = db.transactionDao().findByReference("ABC123")
        assertNotNull(found)
        assertEquals(id, found!!.id)
        assertNull(db.transactionDao().findByReference("MISSING"))
    }

    @Test
    fun dedup_hash_unique_index_blocks_duplicates() = runBlocking {
        db.transactionDao().insert(
            Transaction(type = TransactionType.EXPENSE, amount = 100, dedupHash = "same-hash")
        )
        var threw = false
        try {
            db.transactionDao().insert(
                Transaction(type = TransactionType.EXPENSE, amount = 200, dedupHash = "same-hash")
            )
        } catch (t: Throwable) {
            threw = true
        }
        assertTrue("Expected a unique-constraint failure on dedup_hash", threw)
    }

    @Test
    fun getSuggestedCategory_uses_counterparty_column() = runBlocking {
        db.transactionDao().insert(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = 500,
                categoryId = 1,
                counterparty = "Naivas",
                dedupHash = "h-naivas-1"
            )
        )
        db.transactionDao().insert(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = 700,
                categoryId = 1,
                counterparty = "Naivas",
                dedupHash = "h-naivas-2"
            )
        )
        assertEquals(1, db.transactionDao().getSuggestedCategory("Naivas"))
        assertNull(db.transactionDao().getSuggestedCategory("Unknown"))
    }

    @Test
    fun pagingSource_returns_rows_descending_by_transactedAt() = runBlocking {
        listOf(1_000L, 2_000L, 3_000L).forEachIndexed { i, ts ->
            db.transactionDao().insert(
                Transaction(
                    type = TransactionType.EXPENSE,
                    amount = 100,
                    transactedAt = ts,
                    dedupHash = "h-$i"
                )
            )
        }
        val source = db.transactionDao().pagingSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page
        assertEquals(3, result.data.size)
        assertEquals(3_000L, result.data[0].transactedAt)
        assertEquals(1_000L, result.data[2].transactedAt)
    }

    @Test
    fun recordSyncFailure_increments_counter_and_sets_timestamp() = runBlocking {
        val id = db.transactionDao().insert(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = 100,
                dedupHash = "h-failure"
            )
        ).toInt()
        db.transactionDao().recordSyncFailure(listOf(id), 42L)
        val row = db.transactionDao().findByDedupHash("h-failure")!!
        assertEquals(1, row.syncFailures)
        assertEquals(42L, row.lastSyncAttemptAt)
    }

    @Test
    fun seed_inserts_mpesa_and_airtelmoney_sources() = runBlocking {
        // SeedCallback fires onCreate for in-memory databases too.
        assertNotNull(db.smsSourceDao().findBySender("MPESA"))
        assertNotNull(db.smsSourceDao().findBySender("AIRTELMONEY"))
    }
}
