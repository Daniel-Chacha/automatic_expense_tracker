package com.personal.financetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.Budget
import com.personal.financetracker.data.local.entity.Category
import com.personal.financetracker.ui.theme.Expense
import com.personal.financetracker.ui.theme.Income
import com.personal.financetracker.util.FormatUtils
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cal = Calendar.getInstance()
    val month = FormatUtils.getMonthStart(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    val monthEnd = FormatUtils.getMonthEnd(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))

    val budgets by db.budgetDao().getByMonth(month).collectAsStateWithLifecycle(emptyList())
    val spent by db.transactionDao().getSpentByCategory(month, monthEnd).collectAsStateWithLifecycle(emptyList())
    val categories by db.categoryDao().getByType(false).collectAsStateWithLifecycle(emptyList())

    val spentMap = remember(spent) { spent.associate { it.categoryId to it.total } }
    val budgetMap = remember(budgets) { budgets.associate { it.categoryId to it } }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets — ${FormatUtils.getMonthName(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Set budget") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val budgetedCategories = categories.filter { budgetMap.containsKey(it.id) }
            if (budgetedCategories.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("No budgets set. Tap + to add one.", color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                }
            }
            items(budgetedCategories, key = { it.id }) { cat ->
                val budget = budgetMap[cat.id]!!
                val spentAmt = spentMap[cat.id] ?: 0
                val progress = if (budget.amount > 0) spentAmt.toFloat() / budget.amount else 0f
                val overBudget = spentAmt > budget.amount

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { editingBudget = budget }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cat.icon ?: "📦", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            Text(cat.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(
                                if (overBudget) "Over!" else "${(progress * 100).toInt()}%",
                                color = if (overBudget) Expense else MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (overBudget) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.coerceAtMost(1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (overBudget) Expense else Income,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${FormatUtils.formatAmount(spentAmt)} / ${FormatUtils.formatAmount(budget.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var selectedCatId by remember { mutableIntStateOf(-1) }
        var amountText by remember { mutableStateOf("") }
        val unbugeted = categories.filter { !budgetMap.containsKey(it.id) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Set Budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    unbugeted.forEach { cat ->
                        FilterChip(
                            selected = selectedCatId == cat.id,
                            onClick = { selectedCatId = cat.id },
                            label = { Text("${cat.icon ?: "📦"} ${cat.name}") }
                        )
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Budget (KES)") },
                        singleLine = true,
                        prefix = { Text("KES ") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                    if (selectedCatId >= 0 && amt > 0) {
                        scope.launch { db.budgetDao().insert(Budget(categoryId = selectedCatId, month = month, amount = amt)) }
                        showAddDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    editingBudget?.let { budget ->
        val cat = categories.firstOrNull { it.id == budget.categoryId }
        var amountText by remember(budget.id) { mutableStateOf(String.format("%.2f", budget.amount / 100.0)) }
        AlertDialog(
            onDismissRequest = { editingBudget = null },
            title = { Text("Edit Budget — ${cat?.name ?: "Category"}") },
            text = {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Budget (KES)") },
                    prefix = { Text("KES ") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                    if (amt > 0) {
                        scope.launch { db.budgetDao().insert(budget.copy(amount = amt)) }
                        editingBudget = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch { db.budgetDao().delete(budget) }
                        editingBudget = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { editingBudget = null }) { Text("Cancel") }
                }
            }
        )
    }
}
