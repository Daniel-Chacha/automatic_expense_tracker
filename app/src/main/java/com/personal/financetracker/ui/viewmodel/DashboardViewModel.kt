package com.personal.financetracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.dao.CategoryExpense
import com.personal.financetracker.data.local.dao.CategorySpent
import com.personal.financetracker.data.local.entity.*
import com.personal.financetracker.util.FormatUtils
import com.personal.financetracker.util.RecurringDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.Calendar

data class MonthState(val year: Int, val month: Int)

class DashboardViewModel(private val db: AppDatabase) : ViewModel() {

    private val _month = MutableStateFlow(
        Calendar.getInstance().let { MonthState(it.get(Calendar.YEAR), it.get(Calendar.MONTH)) }
    )
    val month: StateFlow<MonthState> = _month

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthRange = _month.map { m ->
        FormatUtils.getMonthStart(m.year, m.month) to FormatUtils.getMonthEnd(m.year, m.month)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalIncome: StateFlow<Int> = monthRange.flatMapLatest { (start, end) ->
        db.transactionDao().getTotalIncome(start, end).map { it ?: 0 }
    }.guardWith("totalIncome", default = 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalExpense: StateFlow<Int> = monthRange.flatMapLatest { (start, end) ->
        db.transactionDao().getTotalExpense(start, end).map { it ?: 0 }
    }.guardWith("totalExpense", default = 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryBreakdown: StateFlow<List<CategoryExpense>> = monthRange.flatMapLatest { (start, end) ->
        db.transactionDao().getExpenseByCategory(start, end)
    }.guardWith("categoryBreakdown", default = emptyList())

    val recentTransactions: StateFlow<List<Transaction>> =
        db.transactionDao().getRecent(10)
            .guardWith("recentTransactions", default = emptyList())

    val categories = db.categoryDao().getAll()
        .guardWith("categories", default = emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val spentByCategory: StateFlow<List<CategorySpent>> = monthRange.flatMapLatest { (start, end) ->
        db.transactionDao().getSpentByCategory(start, end)
    }.guardWith("spentByCategory", default = emptyList())

    val budgets: StateFlow<List<Budget>> = _month.flatMapLatest { m ->
        db.budgetDao().getByMonth(FormatUtils.getMonthStart(m.year, m.month))
    }.guardWith("budgets", default = emptyList())

    val savingsGoals: StateFlow<List<SavingsGoal>> = db.savingsGoalDao().getActive()
        .guardWith("savingsGoals", default = emptyList())

    val investments: StateFlow<Int> = db.investmentDao().getTotalValue()
        .map { it ?: 0 }
        .guardWith("investments", default = 0)

    val activeDebts: StateFlow<List<Debt>> = db.debtDao().getActive()
        .guardWith("activeDebts", default = emptyList())

    /** Top recurring patterns over the last 90 days. */
    val recurring: StateFlow<List<RecurringDetector.RecurringPattern>> =
        db.transactionDao().getAll()
            .map { all ->
                val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                RecurringDetector.detect(all.filter { it.transactedAt >= cutoff }).take(8)
            }
            .guardWith("recurring", default = emptyList())

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

    private fun <T> Flow<T>.guardWith(label: String, default: T): StateFlow<T> =
        catch { e ->
            Log.e(TAG, "$label flow error", e)
            _errors.tryEmit("$label: ${e.message ?: "unknown error"}")
            emit(default)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), default)

    companion object {
        private const val TAG = "DashboardVM"
    }
}

class DashboardViewModelFactory(private val db: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DashboardViewModel(db) as T
    }
}
