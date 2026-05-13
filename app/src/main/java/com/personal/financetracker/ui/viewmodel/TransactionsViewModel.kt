package com.personal.financetracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.*
import com.personal.financetracker.util.FormatUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TransactionFilter { ALL, INCOME, EXPENSE, UNCATEGORIZED }

class TransactionsViewModel(private val db: AppDatabase) : ViewModel() {

    private val now = Calendar.getInstance()
    private val _selectedYear = MutableStateFlow(now.get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear

    /** 0-indexed (Calendar.MONTH style) so it composes with FormatUtils helpers. */
    private val _selectedMonth = MutableStateFlow(now.get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth

    private val _filter = MutableStateFlow(TransactionFilter.ALL)
    val filter: StateFlow<TransactionFilter> = _filter

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val availableYears: StateFlow<List<Int>> =
        db.transactionDao().getDistinctYears()
            .map { fromTxns ->
                val current = Calendar.getInstance().get(Calendar.YEAR)
                ((2026..(current.coerceAtLeast(2030))).toSet() + fromTxns).sorted()
            }
            .catch { e ->
                Log.e(TAG, "availableYears flow error", e)
                emit((2026..2030).toList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(20_000), (2026..2030).toList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedTransactions: Flow<PagingData<Transaction>> = combine(
        _selectedYear, _selectedMonth, _filter, _searchQuery
    ) { year, month, f, q -> Quadruple(year, month, f, q) }
        .flatMapLatest { (year, month, currentFilter, query) ->
            val start = FormatUtils.getMonthStart(year, month)
            val end = FormatUtils.getMonthEnd(year, month)
            Pager(PagingConfig(pageSize = 30, prefetchDistance = 10)) {
                db.transactionDao().pagingSourceByDateRange(start, end)
            }.flow.map { pagingData ->
                pagingData.filter { txn ->
                    val matchesFilter = when (currentFilter) {
                        TransactionFilter.ALL -> true
                        TransactionFilter.INCOME -> txn.type == TransactionType.INCOME
                        TransactionFilter.EXPENSE -> txn.type == TransactionType.EXPENSE
                        TransactionFilter.UNCATEGORIZED -> txn.status == TransactionStatus.UNCATEGORIZED
                    }
                    val matchesQuery = query.isBlank() ||
                        txn.description?.contains(query, ignoreCase = true) == true ||
                        txn.counterparty?.contains(query, ignoreCase = true) == true
                    matchesFilter && matchesQuery
                }
            }
        }
        .cachedIn(viewModelScope)

    val categories = db.categoryDao().getAll()
        .catch { e ->
            Log.e(TAG, "categories flow error", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(20_000), emptyList())

    fun setFilter(filter: TransactionFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setYear(year: Int) { _selectedYear.value = year }
    fun setMonth(month: Int) { _selectedMonth.value = month.coerceIn(0, 11) }

    fun previousMonth() {
        if (_selectedMonth.value == 0) {
            _selectedYear.value -= 1
            _selectedMonth.value = 11
        } else {
            _selectedMonth.value -= 1
        }
    }

    fun nextMonth() {
        if (_selectedMonth.value == 11) {
            _selectedYear.value += 1
            _selectedMonth.value = 0
        } else {
            _selectedMonth.value += 1
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            com.personal.financetracker.data.HardDeletionService.deleteTransaction(db, transaction)
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            db.transactionDao().update(
                transaction.copy(isSynced = false, updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun updateTransactionCategory(transactionId: Int, categoryId: Int, note: String?) {
        viewModelScope.launch {
            val txn = db.transactionDao().getById(transactionId) ?: return@launch
            db.transactionDao().update(
                txn.copy(
                    categoryId = categoryId,
                    description = note ?: txn.description,
                    status = TransactionStatus.CONFIRMED,
                    isSynced = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun getById(id: Int): Flow<Transaction?> = db.transactionDao().getByIdFlow(id)

    private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        private const val TAG = "TransactionsVM"
    }
}

class TransactionsViewModelFactory(private val db: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TransactionsViewModel(db) as T
    }
}
