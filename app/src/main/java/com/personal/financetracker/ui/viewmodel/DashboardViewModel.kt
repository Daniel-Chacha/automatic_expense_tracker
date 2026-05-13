package com.personal.financetracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.dao.CategoryExpense
import com.personal.financetracker.data.local.dao.CategorySpent
import com.personal.financetracker.data.local.dao.MonthAggregate
import com.personal.financetracker.data.local.dao.YearAggregate
import com.personal.financetracker.data.local.entity.*
import com.personal.financetracker.ui.components.ChartSeries
import com.personal.financetracker.ui.components.MultiChartType
import com.personal.financetracker.ui.components.MultiSeriesData
import com.personal.financetracker.util.FormatUtils
import com.personal.financetracker.util.RecurringDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import androidx.compose.ui.graphics.Color
import java.util.Calendar

data class MonthState(val year: Int, val month: Int)

sealed class YearSelection {
    data object AllTime : YearSelection()
    data class Year(val year: Int) : YearSelection()
}

enum class MetricGroup(val label: String, val color: Color) {
    INCOME("Income", Color(0xFF2ECC71)),
    EXPENSE("Expense", Color(0xFFE74C3C)),
    BUDGET("Budget", Color(0xFFF7DC6F)),
    SAVINGS("Savings", Color(0xFF4ECDC4)),
    INVESTMENT("Investment", Color(0xFFC44DFF))
}

enum class OverviewMode { CATEGORY_DONUT, GROUP_BARS }

class DashboardViewModel(private val db: AppDatabase) : ViewModel() {

    private val _month = MutableStateFlow(
        Calendar.getInstance().let { MonthState(it.get(Calendar.YEAR), it.get(Calendar.MONTH)) }
    )
    val month: StateFlow<MonthState> = _month

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors

    // ── New: yearly selector / chart controls ──────────

    private val _selectedYear = MutableStateFlow<YearSelection>(
        YearSelection.Year(Calendar.getInstance().get(Calendar.YEAR))
    )
    val selectedYear: StateFlow<YearSelection> = _selectedYear

    private val _visibleGroups = MutableStateFlow(setOf(MetricGroup.INCOME, MetricGroup.EXPENSE))
    val visibleGroups: StateFlow<Set<MetricGroup>> = _visibleGroups

    private val _chartType = MutableStateFlow(MultiChartType.BAR)
    val chartType: StateFlow<MultiChartType> = _chartType

    private val _overviewMode = MutableStateFlow(OverviewMode.CATEGORY_DONUT)
    val overviewMode: StateFlow<OverviewMode> = _overviewMode

    // ── Existing monthly flows ────────────────────────

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

    @OptIn(ExperimentalCoroutinesApi::class)
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

    val recurring: StateFlow<List<RecurringDetector.RecurringPattern>> =
        db.transactionDao().getAll()
            .map { all ->
                val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                RecurringDetector.detect(all.filter { it.transactedAt >= cutoff }).take(8)
            }
            .guardWith("recurring", default = emptyList())

    // ── Yearly chart derived flows ─────────────────────

    /** All years that should appear in the dropdown: 2026..2030 ∪ years that have data. */
    val availableYears: StateFlow<List<Int>> = combine(
        db.transactionDao().getDistinctYears(),
        db.snapshotDao().getDistinctYears()
    ) { txnYears, snapYears ->
        ((2026..2030).toSet() + txnYears + snapYears).sorted()
    }.guardWith("availableYears", default = (2026..2030).toList())

    /** Yearly chart: 5-series bars/lines (one per MetricGroup). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val yearlyChartData: StateFlow<MultiSeriesData> = combine(
        _selectedYear,
        _visibleGroups
    ) { year, groups -> year to groups }.flatMapLatest { (selection, visible) ->
        when (selection) {
            is YearSelection.Year -> yearlyByMonth(selection.year, visible)
            is YearSelection.AllTime -> allTimeByYear(visible)
        }
    }.guardWith("yearlyChartData", default = MultiSeriesData(emptyList(), emptyList()))

    private fun yearlyByMonth(year: Int, visible: Set<MetricGroup>): Flow<MultiSeriesData> {
        val labels = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        val yearStart = FormatUtils.getMonthStart(year, 0)
        val yearEnd = FormatUtils.getMonthEnd(year, 11)
        return combine(
            db.transactionDao().getIncomeByMonthForYear(year),
            db.transactionDao().getExpenseByMonthForYear(year),
            db.budgetDao().getByMonthRange(yearStart, yearEnd),
            db.snapshotDao().getByYear(year)
        ) { income, expense, budgets, snapshots ->
            val incomeMap = income.associate { it.month to it.total }
            val expenseMap = expense.associate { it.month to it.total }
            val budgetMap = mutableMapOf<Int, Int>()
            budgets.forEach { b ->
                val cal = Calendar.getInstance().apply { timeInMillis = b.month }
                val m = cal.get(Calendar.MONTH) + 1
                budgetMap[m] = (budgetMap[m] ?: 0) + b.amount
            }
            val savingsMap = snapshots.filter { it.kind == SnapshotKind.SAVINGS }.associate { it.month to it.amount }
            val investmentMap = snapshots.filter { it.kind == SnapshotKind.INVESTMENT }.associate { it.month to it.amount }

            val months = (1..12).toList()
            val seriesAll = listOf(
                MetricGroup.INCOME to months.map { incomeMap[it] },
                MetricGroup.EXPENSE to months.map { expenseMap[it] },
                MetricGroup.BUDGET to months.map { budgetMap[it] },
                MetricGroup.SAVINGS to months.map { savingsMap[it] },
                MetricGroup.INVESTMENT to months.map { investmentMap[it] }
            )
            val filtered = seriesAll.filter { it.first in visible }.map { (group, values) ->
                ChartSeries(label = group.label, color = group.color, values = values)
            }
            MultiSeriesData(xLabels = labels, series = filtered)
        }
    }

    private fun allTimeByYear(visible: Set<MetricGroup>): Flow<MultiSeriesData> {
        return combine(
            db.transactionDao().getIncomeByYear(),
            db.transactionDao().getExpenseByYear(),
            db.budgetDao().getAll(),
            db.snapshotDao().getAll(),
            availableYears
        ) { income, expense, budgets, snapshots, years ->
            val xLabels = years.map { it.toString() }
            val incomeMap = income.associate { it.year to it.total }
            val expenseMap = expense.associate { it.year to it.total }
            val budgetMap = mutableMapOf<Int, Int>()
            budgets.forEach { b ->
                val cal = Calendar.getInstance().apply { timeInMillis = b.month }
                val y = cal.get(Calendar.YEAR)
                budgetMap[y] = (budgetMap[y] ?: 0) + b.amount
            }
            // Year-level savings/investment uses end-of-year snapshot (max month) if available
            val savingsByYear = snapshots.filter { it.kind == SnapshotKind.SAVINGS }
                .groupBy { it.year }
                .mapValues { entry -> entry.value.maxByOrNull { it.month }?.amount }
            val investmentByYear = snapshots.filter { it.kind == SnapshotKind.INVESTMENT }
                .groupBy { it.year }
                .mapValues { entry -> entry.value.maxByOrNull { it.month }?.amount }

            val seriesAll = listOf(
                MetricGroup.INCOME to years.map { incomeMap[it] },
                MetricGroup.EXPENSE to years.map { expenseMap[it] },
                MetricGroup.BUDGET to years.map { budgetMap[it] },
                MetricGroup.SAVINGS to years.map { savingsByYear[it] },
                MetricGroup.INVESTMENT to years.map { investmentByYear[it] }
            )
            val filtered = seriesAll.filter { it.first in visible }.map { (group, values) ->
                ChartSeries(label = group.label, color = group.color, values = values)
            }
            MultiSeriesData(xLabels = xLabels, series = filtered)
        }
    }

    /** Whole-year expense breakdown for the donut. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val yearCategoryBreakdown: StateFlow<List<CategoryExpense>> = _selectedYear.flatMapLatest { sel ->
        val year = (sel as? YearSelection.Year)?.year
        if (year != null) db.transactionDao().getExpenseByCategoryForYear(year)
        else db.transactionDao().getExpenseByCategoryForYear(Calendar.getInstance().get(Calendar.YEAR))
    }.guardWith("yearCategoryBreakdown", default = emptyList())

    /** Annual totals across all 5 groups, used by the toggleable overview's bar view. */
    val yearGroupTotals: StateFlow<Map<MetricGroup, Int>> = yearlyChartData
        .map { data ->
            val totals = mutableMapOf<MetricGroup, Int>()
            MetricGroup.entries.forEach { g ->
                val series = data.series.firstOrNull { it.label == g.label }
                totals[g] = series?.values?.filterNotNull()?.let { vals ->
                    when (g) {
                        MetricGroup.SAVINGS, MetricGroup.INVESTMENT -> vals.maxOrNull() ?: 0
                        else -> vals.sum()
                    }
                } ?: 0
            }
            totals
        }
        .guardWith("yearGroupTotals", default = emptyMap())

    // ── Setters ────────────────────────────────────────

    fun setYearSelection(selection: YearSelection) {
        _selectedYear.value = selection
    }

    fun toggleGroup(group: MetricGroup) {
        _visibleGroups.update { current ->
            if (group in current && current.size > 1) current - group
            else current + group
        }
    }

    fun setChartType(type: MultiChartType) {
        _chartType.value = type
    }

    fun setOverviewMode(mode: OverviewMode) {
        _overviewMode.value = mode
    }

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
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(20_000), default)

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
