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
import com.personal.financetracker.data.HardDeletionService
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.Debt
import com.personal.financetracker.data.local.entity.DebtDirection
import com.personal.financetracker.ui.components.DeleteConfirmDialog
import com.personal.financetracker.ui.theme.Expense
import com.personal.financetracker.ui.theme.Income
import com.personal.financetracker.ui.theme.Warning
import com.personal.financetracker.util.FormatUtils
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
    var editDebt by remember { mutableStateOf<Debt?>(null) }

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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { editDebt = debt }
                    ) {
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
        DebtDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { person, amount, direction ->
                scope.launch {
                    db.debtDao().insert(Debt(direction = direction, person = person, amount = amount))
                }
                showAddDialog = false
            },
            onDelete = null
        )
    }

    var deletingDebt by remember { mutableStateOf<Debt?>(null) }

    editDebt?.let { debt ->
        DebtDialog(
            initial = debt,
            onDismiss = { editDebt = null },
            onSave = { person, amount, direction ->
                scope.launch {
                    // Bump updatedAt so MetadataSyncRepository pushes the edit.
                    db.debtDao().update(
                        debt.copy(
                            person = person,
                            amount = amount,
                            direction = direction,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                editDebt = null
            },
            onDelete = {
                deletingDebt = debt
                editDebt = null
            }
        )
    }

    deletingDebt?.let { debt ->
        DeleteConfirmDialog(
            itemDescription = "the debt entry for ${debt.person}",
            onDismiss = { deletingDebt = null },
            onConfirm = {
                scope.launch { HardDeletionService.deleteDebt(db, debt) }
                deletingDebt = null
            }
        )
    }
}

@Composable
private fun DebtDialog(
    initial: Debt?,
    onDismiss: () -> Unit,
    onSave: (person: String, amountCents: Int, direction: DebtDirection) -> Unit,
    onDelete: (() -> Unit)?
) {
    var person by remember { mutableStateOf(initial?.person ?: "") }
    var amountText by remember {
        mutableStateOf(initial?.let { String.format("%.2f", it.amount / 100.0) } ?: "")
    }
    var direction by remember { mutableStateOf(initial?.direction ?: DebtDirection.LENT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Debt" else "Edit Debt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebtDirection.entries.forEach { d ->
                        FilterChip(
                            selected = direction == d,
                            onClick = { direction = d },
                            label = { Text(if (d == DebtDirection.LENT) "I Lent" else "I Borrowed") }
                        )
                    }
                }
                OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text("Person") }, singleLine = true)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (KES)") }, prefix = { Text("KES ") }, singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                if (person.isNotBlank() && amt > 0) onSave(person, amt, direction)
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
