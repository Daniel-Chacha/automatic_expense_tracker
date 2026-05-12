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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.Investment
import com.personal.financetracker.ui.theme.Expense
import com.personal.financetracker.ui.theme.Income
import com.personal.financetracker.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentsScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val investments by db.investmentDao().getAll().collectAsStateWithLifecycle(emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editInvestment by remember { mutableStateOf<Investment?>(null) }

    val totalInvested = investments.sumOf { it.buyInAmount }
    val totalValue = investments.sumOf { it.currentValue }
    val totalReturn = totalValue - totalInvested
    val returnPercent = if (totalInvested > 0) (totalReturn.toFloat() / totalInvested * 100) else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Investments") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Add") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Portfolio summary
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(0.3f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Portfolio Value", style = MaterialTheme.typography.bodySmall)
                            Text(FormatUtils.formatAmount(totalValue), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = (if (totalReturn >= 0) Income else Expense).copy(0.12f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Returns", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${if (totalReturn >= 0) "+" else ""}${FormatUtils.formatAmount(totalReturn)} (${String.format("%.1f", returnPercent)}%)",
                                fontWeight = FontWeight.Bold, color = if (totalReturn >= 0) Income else Expense,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }

            items(investments, key = { it.id }) { inv ->
                val ret = inv.currentValue - inv.buyInAmount
                val retPct = if (inv.buyInAmount > 0) ret.toFloat() / inv.buyInAmount * 100 else 0f
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { editInvestment = inv }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(inv.name, fontWeight = FontWeight.Medium)
                                inv.type?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f)) }
                            }
                            Text(
                                "${if (ret >= 0) "+" else ""}${String.format("%.1f", retPct)}%",
                                color = if (ret >= 0) Income else Expense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Invested: ${FormatUtils.formatAmount(inv.buyInAmount)}", style = MaterialTheme.typography.bodySmall)
                            Text("Value: ${FormatUtils.formatAmount(inv.currentValue)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        InvestmentDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, type, buyIn, current ->
                scope.launch {
                    db.investmentDao().insert(
                        Investment(name = name, type = type, buyInAmount = buyIn, currentValue = current)
                    )
                }
                showAddDialog = false
            },
            onDelete = null
        )
    }

    editInvestment?.let { inv ->
        InvestmentDialog(
            initial = inv,
            onDismiss = { editInvestment = null },
            onSave = { name, type, buyIn, current ->
                scope.launch {
                    db.investmentDao().update(
                        inv.copy(
                            name = name,
                            type = type,
                            buyInAmount = buyIn,
                            currentValue = current,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                editInvestment = null
            },
            onDelete = {
                scope.launch { db.investmentDao().delete(inv) }
                editInvestment = null
            }
        )
    }
}

@Composable
private fun InvestmentDialog(
    initial: Investment?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String?, buyInCents: Int, currentCents: Int) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "") }
    var buyInText by remember {
        mutableStateOf(initial?.let { String.format("%.2f", it.buyInAmount / 100.0) } ?: "")
    }
    var valueText by remember {
        mutableStateOf(initial?.let { String.format("%.2f", it.currentValue / 100.0) } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Investment" else "Edit Investment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type (mmf, sacco, stock)") }, singleLine = true)
                OutlinedTextField(
                    value = buyInText,
                    onValueChange = { buyInText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Buy-in amount (KES)") }, prefix = { Text("KES ") }, singleLine = true
                )
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Current value (KES)") }, prefix = { Text("KES ") }, singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val buyIn = ((buyInText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                val current = ((valueText.toDoubleOrNull() ?: buyInText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                if (name.isNotBlank() && buyIn > 0) {
                    onSave(name, type.ifBlank { null }, buyIn, current.takeIf { it > 0 } ?: buyIn)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
