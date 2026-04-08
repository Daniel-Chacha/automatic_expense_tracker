package com.personal.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.dao.CategoryExpense
import com.personal.expensetracker.data.local.dao.CategorySpent
import com.personal.expensetracker.data.local.entity.*
import com.personal.expensetracker.util.FormatUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.Calendar

data class MonthState(val year: Int, val month: Int)

class DashboardViewModel(private val db: AppDatabase) : ViewModel() {

    private val _month = MutableStateFlow(
        Calendar.getInstance().let { MonthState(it.get(Calendar.YEAR), it.get(Calendar.MONTH)) }
    )
    val month: StateFlow<MonthState> = _month

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthRange = _month.map { m ->
        FormatUtils.getMonthStart(m.year, m.month) to FormatUtils.getMonthEnd(m.year, m.month)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalIncome: StateFlow<Int> = monthRange.flatMapLatest { (start, end) ->
        db.transactionDao().getTotalIncome(start, end).map { it ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalExpense: StateFlow<Int> = monthRange.flatMapLatest { (start, end) ->
        db.transactionDao().getTotalExpense(start, end).map { it ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryBreakdown: StateFlow<List<CategoryExpense>> = monthRange.flatMapLatest { (start, end) ->
        db.transactionDao().getExpenseByCategory(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTransactions: StateFlow<List<Transaction>> =
        db.transactionDao().getRecent(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories = db.categoryDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Phase 4+5: Financial overview ──

    @OptIn(ExperimentalCoroutinesApi::class)
    val spentByCategory: StateFlow<List<CategorySpent>> = monthRange.flatMapLatest { (start, end) ->
        db.transactionDao().getSpentByCategory(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgets: StateFlow<List<Budget>> = _month.flatMapLatest { m ->
        db.budgetDao().getByMonth(FormatUtils.getMonthStart(m.year, m.month))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savingsGoals: StateFlow<List<SavingsGoal>> = db.savingsGoalDao().getActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val investments: StateFlow<Int> = db.investmentDao().getTotalValue()
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val activeDebts: StateFlow<List<Debt>> = db.debtDao().getActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun previousMonth() {
        _month.update { m ->
            if (m.month == 0) MonthState(m.year - 1, 11)
            else MonthState(m.year, m.month - 1)
        }
    }

    fun nextMonth() {
        _month.update { m ->
            if (m.month == 11) MonthState(m.year + 1, 0)
            else MonthState(m.year, m.month + 1)
        }
    }
}

class DashboardViewModelFactory(private val db: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DashboardViewModel(db) as T
    }
}
