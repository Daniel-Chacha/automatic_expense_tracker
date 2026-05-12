package com.personal.financetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.personal.financetracker.ui.components.TransactionCard
import com.personal.financetracker.ui.navigation.SubRoutes
import com.personal.financetracker.ui.theme.Expense
import com.personal.financetracker.ui.theme.Income
import com.personal.financetracker.ui.theme.Warning
import com.personal.financetracker.ui.viewmodel.DashboardViewModel
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

    // Phase 4+5 data
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val spentByCategory by viewModel.spentByCategory.collectAsStateWithLifecycle()
    val savingsGoals by viewModel.savingsGoals.collectAsStateWithLifecycle()
    val investmentTotal by viewModel.investments.collectAsStateWithLifecycle()
    val activeDebts by viewModel.activeDebts.collectAsStateWithLifecycle()
    val recurring by viewModel.recurring.collectAsStateWithLifecycle()

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val spentMap = remember(spentByCategory) { spentByCategory.associate { it.categoryId to it.total } }
    val recentTrend = remember(recentTransactions) {
        // Group last 30 days into 6 buckets of 5 days each, summing expenses.
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Month Selector ──
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.previousMonth() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }
                Text(FormatUtils.getMonthName(month.year, month.month), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.nextMonth() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next") }
            }
        }

        // ── Summary Cards ──
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

        // ── Budget Alerts ──
        val overBudget = budgets.filter { b ->
            (spentMap[b.categoryId] ?: 0) > b.amount
        }
        if (overBudget.isNotEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = Expense.copy(0.10f)) {
                    Column(Modifier.padding(12.dp).clickable { onNavigate(SubRoutes.BUDGETS) }) {
                        Text("⚠️ Over Budget", fontWeight = FontWeight.Bold, color = Expense)
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

        // ── Financial Overview Cards ──
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Savings
                FinanceCard(
                    emoji = "🎯",
                    title = "Savings",
                    value = FormatUtils.formatAmount(savingsGoals.sumOf { it.currentAmount }),
                    subtitle = "${savingsGoals.size} goal${if (savingsGoals.size != 1) "s" else ""}",
                    modifier = Modifier.weight(1f).clickable { onNavigate(SubRoutes.SAVINGS) }
                )
                // Investments
                FinanceCard(
                    emoji = "📈",
                    title = "Investments",
                    value = FormatUtils.formatAmount(investmentTotal),
                    subtitle = "portfolio value",
                    modifier = Modifier.weight(1f).clickable { onNavigate(SubRoutes.INVESTMENTS) }
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Debts
                val totalLent = activeDebts.filter { it.direction == DebtDirection.LENT }.sumOf { it.amount }
                val totalBorrowed = activeDebts.filter { it.direction == DebtDirection.BORROWED }.sumOf { it.amount }
                FinanceCard(
                    emoji = "🤝",
                    title = "Debts",
                    value = "${activeDebts.size} active",
                    subtitle = "Lent: ${FormatUtils.formatAmountShort(totalLent)} | Owe: ${FormatUtils.formatAmountShort(totalBorrowed)}",
                    modifier = Modifier.weight(1f).clickable { onNavigate(SubRoutes.DEBTS) }
                )
                // Budgets
                FinanceCard(
                    emoji = "💰",
                    title = "Budgets",
                    value = "${budgets.size} set",
                    subtitle = if (overBudget.isEmpty()) "All on track" else "${overBudget.size} over",
                    modifier = Modifier.weight(1f).clickable { onNavigate(SubRoutes.BUDGETS) }
                )
            }
        }

        // ── Expense Breakdown Chart ──
        if (categoryBreakdown.isNotEmpty()) {
            item { Text("Expense Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(segments = segments, centerText = FormatUtils.formatAmountShort(totalExpense))
                    ChartLegend(segments = segments, modifier = Modifier.weight(1f))
                }
            }
        }

        // ── Spend Trend (last 30 days, 5-day buckets) ──
        if (recentTrend.any { it > 0 }) {
            item { Text("Spend Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    MonthlyTrendChart(points = recentTrend, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // ── Recurring Patterns ──
        if (recurring.isNotEmpty()) {
            item { Text("Recurring", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(recurring, key = { it.counterparty }) { pattern ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
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

        // ── Recent Transactions ──
        if (recentTransactions.isNotEmpty()) {
            item { Text("Recent Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(recentTransactions, key = { it.id }) { txn ->
                val cat = txn.categoryId?.let { categoryMap[it] }
                TransactionCard(transaction = txn, categoryName = cat?.name, categoryIcon = cat?.icon)
            }
        }

        // ── Empty State ──
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
private fun FinanceCard(emoji: String, title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp)) {
            Text("$emoji $title", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
    }
}
