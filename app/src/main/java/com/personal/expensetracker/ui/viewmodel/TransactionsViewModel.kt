package com.personal.expensetracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.entity.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TransactionFilter { ALL, INCOME, EXPENSE, UNCATEGORIZED }

class TransactionsViewModel(private val db: AppDatabase) : ViewModel() {

    private val _filter = MutableStateFlow(TransactionFilter.ALL)
    val filter: StateFlow<TransactionFilter> = _filter

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedTransactions: Flow<PagingData<Transaction>> = combine(_filter, _searchQuery) { f, q -> f to q }
        .flatMapLatest { (currentFilter, query) ->
            Pager(PagingConfig(pageSize = 30, prefetchDistance = 10)) {
                db.transactionDao().pagingSource()
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: TransactionFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            db.transactionDao().delete(transaction)
        }
    }

    fun updateTransactionCategory(transactionId: Int, categoryId: Int, note: String?) {
        viewModelScope.launch {
            val txn = db.transactionDao().getAll().first().find { it.id == transactionId } ?: return@launch
            db.transactionDao().update(
                txn.copy(
                    categoryId = categoryId,
                    description = note ?: txn.description,
                    status = TransactionStatus.CONFIRMED
                )
            )
        }
    }

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
