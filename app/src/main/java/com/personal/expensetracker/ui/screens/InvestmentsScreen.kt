package com.personal.expensetracker.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.entity.Investment
import com.personal.expensetracker.ui.theme.Expense
import com.personal.expensetracker.ui.theme.Income
import com.personal.expensetracker.util.FormatUtils
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
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
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
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { editInvestment = inv }, modifier = Modifier.fillMaxWidth()) { Text("Update Value") }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Investment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type (mmf, sacco, stock)") }, singleLine = true)
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount (KES)") }, prefix = { Text("KES ") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = {
                val amt = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                if (name.isNotBlank() && amt > 0) { scope.launch { db.investmentDao().insert(Investment(name = name, type = type.ifBlank { null }, buyInAmount = amt, currentValue = amt)) }; showAddDialog = false }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    editInvestment?.let { inv ->
        var valueText by remember { mutableStateOf(String.format("%.2f", inv.currentValue / 100.0)) }
        AlertDialog(
            onDismissRequest = { editInvestment = null },
            title = { Text("Update: ${inv.name}") },
            text = { OutlinedTextField(value = valueText, onValueChange = { valueText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Current value (KES)") }, prefix = { Text("KES ") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                val v = ((valueText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                scope.launch { db.investmentDao().update(inv.copy(currentValue = v, updatedAt = System.currentTimeMillis())) }; editInvestment = null
            }) { Text("Update") } },
            dismissButton = { TextButton(onClick = { editInvestment = null }) { Text("Cancel") } }
        )
    }
}
