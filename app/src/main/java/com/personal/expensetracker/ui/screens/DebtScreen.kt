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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.entity.Debt
import com.personal.expensetracker.data.local.entity.DebtDirection
import com.personal.expensetracker.ui.theme.Expense
import com.personal.expensetracker.ui.theme.Income
import com.personal.expensetracker.ui.theme.Warning
import com.personal.expensetracker.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val activeDebts by db.debtDao().getActive().collectAsStateWithLifecycle(emptyList())
    val allDebts by db.debtDao().getAll().collectAsStateWithLifecycle(emptyList())
    val debts = if (selectedTab == 0) activeDebts else allDebts.filter { it.isSettled }
    var showAddDialog by remember { mutableStateOf(false) }

    val totalLent = activeDebts.filter { it.direction == DebtDirection.LENT }.sumOf { it.amount }
    val totalBorrowed = activeDebts.filter { it.direction == DebtDirection.BORROWED }.sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debts") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Add") } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Summary
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = Income.copy(0.12f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Owed to you", style = MaterialTheme.typography.bodySmall, color = Income)
                        Text(FormatUtils.formatAmount(totalLent), fontWeight = FontWeight.Bold, color = Income)
                    }
                }
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = Expense.copy(0.12f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("You owe", style = MaterialTheme.typography.bodySmall, color = Expense)
                        Text(FormatUtils.formatAmount(totalBorrowed), fontWeight = FontWeight.Bold, color = Expense)
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Active") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Settled") })
            }

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(debts, key = { it.id }) { debt ->
                    val color = if (debt.direction == DebtDirection.LENT) Income else Expense
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(16.dp)) {
                            Row {
                                Column(Modifier.weight(1f)) {
                                    Text(debt.person, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (debt.direction == DebtDirection.LENT) "You lent" else "You borrowed",
                                        style = MaterialTheme.typography.bodySmall, color = color
                                    )
                                    debt.dueDate?.let { Text("Due: ${FormatUtils.formatDate(it)}", style = MaterialTheme.typography.bodySmall, color = Warning) }
                                }
                                Text(FormatUtils.formatAmount(debt.amount), fontWeight = FontWeight.Bold, color = color)
                            }
                            if (!debt.isSettled) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { scope.launch { db.debtDao().settle(debt.id) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = color)
                                ) { Text("Mark Settled") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var person by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var direction by remember { mutableStateOf(DebtDirection.LENT) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Debt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DebtDirection.entries.forEach { d ->
                            FilterChip(selected = direction == d, onClick = { direction = d },
                                label = { Text(if (d == DebtDirection.LENT) "I Lent" else "I Borrowed") })
                        }
                    }
                    OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text("Person") }, singleLine = true)
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount (KES)") }, prefix = { Text("KES ") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = {
                val amt = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                if (person.isNotBlank() && amt > 0) { scope.launch { db.debtDao().insert(Debt(direction = direction, person = person, amount = amt)) }; showAddDialog = false }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}
