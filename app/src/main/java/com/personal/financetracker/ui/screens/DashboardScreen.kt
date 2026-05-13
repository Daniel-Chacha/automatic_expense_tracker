package com.personal.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.financetracker.data.local.entity.DebtDirection
import com.personal.financetracker.ui.components.ChartLegend
import com.personal.financetracker.ui.components.ChartSegment
import com.personal.financetracker.ui.components.DonutChart
import com.personal.financetracker.ui.components.MonthlyTrendChart
import com.personal.financetracker.ui.components.MultiChartType
import com.personal.financetracker.ui.components.MultiSeriesChart
import com.personal.financetracker.ui.navigation.SubRoutes
import com.personal.financetracker.ui.theme.Expense
import com.personal.financetracker.ui.theme.Income
import com.personal.financetracker.ui.viewmodel.DashboardViewModel
import com.personal.financetracker.ui.viewmodel.MetricGroup
import com.personal.financetracker.ui.viewmodel.OverviewMode
import com.personal.financetracker.ui.viewmodel.YearSelection
import com.personal.financetracker.util.FormatUtils

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigate: (String) -> Unit = {}
) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val totalIncome by viewModel.totalIncome.collectAsStateWithLifecycle()
    val totalExpense by viewModel.totalExpense.collectAsStateWithLifecycle()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsStateWithLifecycle()
    val recentTransactions by viewModel.recentTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val spentByCategory by viewModel.spentByCategory.collectAsStateWithLifecycle()
    val savingsGoals by viewModel.savingsGoals.collectAsStateWithLifecycle()
    val investmentTotal by viewModel.investments.collectAsStateWithLifecycle()
    val activeDebts by viewModel.activeDebts.collectAsStateWithLifecycle()
    val recurring by viewModel.recurring.collectAsStateWithLifecycle()

    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val visibleGroups by viewModel.visibleGroups.collectAsStateWithLifecycle()
    val chartType by viewModel.chartType.collectAsStateWithLifecycle()
    val overviewMode by viewModel.overviewMode.collectAsStateWithLifecycle()
    val availableYears by viewModel.availableYears.collectAsStateWithLifecycle()
    val yearlyChartData by viewModel.yearlyChartData.collectAsStateWithLifecycle()
    val yearCategoryBreakdown by viewModel.yearCategoryBreakdown.collectAsStateWithLifecycle()
    val yearGroupTotals by viewModel.yearGroupTotals.collectAsStateWithLifecycle()

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val spentMap = remember(spentByCategory) { spentByCategory.associate { it.categoryId to it.total } }
    val recentTrend = remember(recentTransactions) {
        val now = System.currentTimeMillis()
        val bucketMs = 5L * 24 * 60 * 60 * 1000
        val buckets = IntArray(6)
        recentTransactions
            .filter { it.type == com.personal.financetracker.data.local.entity.TransactionType.EXPENSE }
            .forEach { txn ->
                val daysAgo = ((now - txn.transactedAt) / bucketMs).toInt()
                if (daysAgo in 0..5) buckets[5 - daysAgo] += txn.amount
            }
        buckets.toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Yearly Overview card ─────────────────────────
        item { SectionHeader("Yearly Overview") }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    YearlyOverviewControls(
                        selectedYear = selectedYear,
                        availableYears = availableYears,
                        onYearSelected = { viewModel.setYearSelection(it) },
                        visibleGroups = visibleGroups,
                        onToggleGroup = { viewModel.toggleGroup(it) },
                        chartType = chartType,
                        onChartTypeSelected = { viewModel.setChartType(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    MultiSeriesChart(data = yearlyChartData, type = chartType)
                }
            }
        }

        // ── Yearly Summary toggleable card ───────────────
        item { SectionHeader("Yearly Summary") }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    OverviewModeToggle(
                        mode = overviewMode,
                        onModeSelected = { viewModel.setOverviewMode(it) }
                    )
                    Spacer(Modifier.height(16.dp))
                    when (overviewMode) {
                        OverviewMode.CATEGORY_DONUT -> YearlyDonut(yearCategoryBreakdown)
                        OverviewMode.GROUP_BARS -> YearlyGroupBars(yearGroupTotals)
                    }
                }
            }
        }

        // ── Month selector (moved down) ──────────────────
        item { SectionHeader("This Month") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous")
                }
                Text(
                    FormatUtils.getMonthName(month.year, month.month),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next")
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Income", totalIncome, Income, Modifier.weight(1f))
                SummaryCard("Expense", totalExpense, Expense, Modifier.weight(1f))
            }
        }
        item {
            val net = totalIncome - totalExpense
            SummaryCard("Net Balance", net, if (net >= 0) Income else Expense, Modifier.fillMaxWidth())
        }

        // Over-budget alert (current month)
        val overBudget = budgets.filter { b -> (spentMap[b.categoryId] ?: 0) > b.amount }
        if (overBudget.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Expense.copy(0.10f),
                    modifier = Modifier.fillMaxWidth().clickable { onNavigate(SubRoutes.BUDGETS) }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️ Over Budget", fontWeight = FontWeight.Bold, color = Expense, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open budgets", tint = Expense)
                        }
                        overBudget.forEach { b ->
                            val cat = categoryMap[b.categoryId]
                            val spent = spentMap[b.categoryId] ?: 0
                            Text(
                                "${cat?.icon ?: ""} ${cat?.name ?: "?"}: ${FormatUtils.formatAmount(spent)} / ${FormatUtils.formatAmount(b.amount)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // ── Quick-access cards ──────────────────────────
        item { SectionHeader("Manage") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NavCard(
                    emoji = "🎯", title = "Savings",
                    value = FormatUtils.formatAmount(savingsGoals.sumOf { it.currentAmount }),
                    subtitle = "${savingsGoals.size} goal${if (savingsGoals.size != 1) "s" else ""}",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SubRoutes.SAVINGS) }
                )
                NavCard(
                    emoji = "📈", title = "Investments",
                    value = FormatUtils.formatAmount(investmentTotal),
                    subtitle = "portfolio value",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SubRoutes.INVESTMENTS) }
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val totalLent = activeDebts.filter { it.direction == DebtDirection.LENT }.sumOf { it.amount }
                val totalBorrowed = activeDebts.filter { it.direction == DebtDirection.BORROWED }.sumOf { it.amount }
                NavCard(
                    emoji = "🤝", title = "Debts",
                    value = "${activeDebts.size} active",
                    subtitle = "Lent ${FormatUtils.formatAmountShort(totalLent)} · Owe ${FormatUtils.formatAmountShort(totalBorrowed)}",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SubRoutes.DEBTS) }
                )
                NavCard(
                    emoji = "💰", title = "Budgets",
                    value = "${budgets.size} set",
                    subtitle = if (overBudget.isEmpty()) "All on track" else "${overBudget.size} over",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(SubRoutes.BUDGETS) }
                )
            }
        }

        // ── Current-month breakdown ─────────────────────
        if (categoryBreakdown.isNotEmpty()) {
            item { SectionHeader("Expense Breakdown — ${FormatUtils.getMonthName(month.year, month.month)}") }
            item {
                val totalExp = categoryBreakdown.sumOf { it.total }.coerceAtLeast(1)
                val defaultColors = listOf(
                    Color(0xFFFF6B35), Color(0xFF4ECDC4), Color(0xFF45B7D1),
                    Color(0xFFC44DFF), Color(0xFFFF6B6B), Color(0xFFF7DC6F),
                    Color(0xFF96CEB4), Color(0xFFDDA0DD), Color(0xFFE67E22)
                )
                val segments = categoryBreakdown.mapIndexed { i, ce ->
                    ChartSegment(
                        label = ce.name, value = ce.total,
                        color = ce.color?.let {
                            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { defaultColors[i % defaultColors.size] }
                        } ?: defaultColors[i % defaultColors.size],
                        percentage = ce.total.toFloat() / totalExp
                    )
                }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DonutChart(segments = segments, centerText = FormatUtils.formatAmountShort(totalExpense))
                        ChartLegend(segments = segments, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (recentTrend.any { it > 0 }) {
            item { SectionHeader("Spend Trend (30d)") }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    MonthlyTrendChart(points = recentTrend, modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
            }
        }

        if (recurring.isNotEmpty()) {
            item { SectionHeader("Recurring") }
            items(recurring) { pattern ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pattern.counterparty, fontWeight = FontWeight.Medium)
                            Text(
                                "${pattern.occurrences}× • avg ${FormatUtils.formatAmount(pattern.averageAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                            )
                        }
                    }
                }
            }
        }

        // ── CTA to History (replaces Recent Transactions list) ──
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable { onNavigate("transactions") }
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("View transactions", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Full searchable history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open history")
                }
            }
        }

        if (recentTransactions.isEmpty() && categoryBreakdown.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊", style = MaterialTheme.typography.displayLarge)
                        Spacer(Modifier.height(12.dp))
                        Text("No transactions yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Text("SMS transactions will appear here automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    }
                }
            }
        }
    }
}

// LazyColumn.items helper for List<T>
private fun <T> androidx.compose.foundation.lazy.LazyListScope.items(
    list: List<T>,
    itemContent: @Composable (T) -> Unit
) = items(list.size) { idx -> itemContent(list[idx]) }

// ── Sub-composables ─────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearlyOverviewControls(
    selectedYear: YearSelection,
    availableYears: List<Int>,
    onYearSelected: (YearSelection) -> Unit,
    visibleGroups: Set<MetricGroup>,
    onToggleGroup: (MetricGroup) -> Unit,
    chartType: MultiChartType,
    onChartTypeSelected: (MultiChartType) -> Unit
) {
    var yearExpanded by remember { mutableStateOf(false) }
    var groupsExpanded by remember { mutableStateOf(false) }

    val yearLabel = when (selectedYear) {
        is YearSelection.AllTime -> "All Time"
        is YearSelection.Year -> selectedYear.year.toString()
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Year dropdown
            Box {
                AssistChip(
                    onClick = { yearExpanded = true },
                    label = { Text(yearLabel) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                )
                DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                    availableYears.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year.toString()) },
                            onClick = {
                                onYearSelected(YearSelection.Year(year))
                                yearExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("All Time") },
                        onClick = {
                            onYearSelected(YearSelection.AllTime)
                            yearExpanded = false
                        }
                    )
                }
            }

            // Groups multi-select
            Box {
                AssistChip(
                    onClick = { groupsExpanded = true },
                    label = { Text("Groups (${visibleGroups.size})") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                )
                DropdownMenu(expanded = groupsExpanded, onDismissRequest = { groupsExpanded = false }) {
                    MetricGroup.entries.forEach { g ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = g in visibleGroups,
                                        onCheckedChange = { onToggleGroup(g) }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(g.color, RoundedCornerShape(50))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(g.label)
                                }
                            },
                            onClick = { onToggleGroup(g) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Chart-type segmented buttons (Bar / Line)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = chartType == MultiChartType.BAR,
                onClick = { onChartTypeSelected(MultiChartType.BAR) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Bar") }
            SegmentedButton(
                selected = chartType == MultiChartType.LINE,
                onClick = { onChartTypeSelected(MultiChartType.LINE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Line") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewModeToggle(
    mode: OverviewMode,
    onModeSelected: (OverviewMode) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == OverviewMode.CATEGORY_DONUT,
            onClick = { onModeSelected(OverviewMode.CATEGORY_DONUT) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text("By category") }
        SegmentedButton(
            selected = mode == OverviewMode.GROUP_BARS,
            onClick = { onModeSelected(OverviewMode.GROUP_BARS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text("Yearly totals") }
    }
}

@Composable
private fun YearlyDonut(breakdown: List<com.personal.financetracker.data.local.dao.CategoryExpense>) {
    if (breakdown.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No expenses this year yet", color = MaterialTheme.colorScheme.onSurface.copy(0.5f), style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val total = breakdown.sumOf { it.total }.coerceAtLeast(1)
    val defaultColors = listOf(
        Color(0xFFFF6B35), Color(0xFF4ECDC4), Color(0xFF45B7D1),
        Color(0xFFC44DFF), Color(0xFFFF6B6B), Color(0xFFF7DC6F),
        Color(0xFF96CEB4), Color(0xFFDDA0DD), Color(0xFFE67E22)
    )
    val segments = breakdown.mapIndexed { i, ce ->
        ChartSegment(
            label = ce.name,
            value = ce.total,
            color = ce.color?.let {
                try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { defaultColors[i % defaultColors.size] }
            } ?: defaultColors[i % defaultColors.size],
            percentage = ce.total.toFloat() / total
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DonutChart(segments = segments, centerText = FormatUtils.formatAmountShort(total))
        ChartLegend(segments = segments, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun YearlyGroupBars(totals: Map<MetricGroup, Int>) {
    val maxValue = (totals.values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricGroup.entries.forEach { g ->
            val v = totals[g] ?: 0
            val frac = v.toFloat() / maxValue
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(g.color, RoundedCornerShape(50)))
                    Spacer(Modifier.width(8.dp))
                    Text(g.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(FormatUtils.formatAmount(v), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { frac.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = g.color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, amount: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.12f), tonalElevation = 2.dp) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = color.copy(0.8f))
            Spacer(Modifier.height(4.dp))
            Text(FormatUtils.formatAmount(kotlin.math.abs(amount)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun NavCard(
    emoji: String,
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier.clickable { onClick() },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$emoji $title", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
        }
    }
}
