package com.personal.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.entity.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TransactionFilter { ALL, INCOME, EXPENSE, UNCATEGORIZED }

class TransactionsViewModel(private val db: AppDatabase) : ViewModel() {

    private val _filter = MutableStateFlow(TransactionFilter.ALL)
    val filter: StateFlow<TransactionFilter> = _filter

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val transactions: StateFlow<List<Transaction>> = combine(
        db.transactionDao().getAll(),
        _filter,
        _searchQuery
    ) { all, filter, query ->
        all.filter { txn ->
            val matchesFilter = when (filter) {
                TransactionFilter.ALL -> true
                TransactionFilter.INCOME -> txn.type == TransactionType.INCOME
                TransactionFilter.EXPENSE -> txn.type == TransactionType.EXPENSE
                TransactionFilter.UNCATEGORIZED -> txn.status == TransactionStatus.UNCATEGORIZED
            }
            val matchesQuery = query.isBlank() || txn.description?.contains(query, ignoreCase = true) == true
                    || txn.meta?.contains(query, ignoreCase = true) == true
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories = db.categoryDao().getAll()
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
}

class TransactionsViewModelFactory(private val db: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TransactionsViewModel(db) as T
    }
}
