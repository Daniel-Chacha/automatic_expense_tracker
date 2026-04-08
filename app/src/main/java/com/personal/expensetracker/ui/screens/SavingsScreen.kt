package com.personal.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.entity.SavingsGoal
import com.personal.expensetracker.ui.theme.Income
import com.personal.expensetracker.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val goals by db.savingsGoalDao().getAll().collectAsStateWithLifecycle(emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editGoal by remember { mutableStateOf<SavingsGoal?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Savings Goals") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Add goal") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val totalSaved = goals.sumOf { it.currentAmount }
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = Income.copy(alpha = 0.12f)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Total Saved", style = MaterialTheme.typography.bodySmall, color = Income.copy(0.8f))
                        Text(FormatUtils.formatAmount(totalSaved), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Income)
                    }
                }
            }

            items(goals, key = { it.id }) { goal ->
                val progress = if (goal.targetAmount > 0) goal.currentAmount.toFloat() / goal.targetAmount else 0f
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(goal.name, fontWeight = FontWeight.Medium)
                                goal.deadline?.let { Text("Due: ${FormatUtils.formatDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f)) }
                            }
                            if (goal.isCompleted) Icon(Icons.Default.Check, "Complete", tint = Income)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.coerceAtMost(1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = Income
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${FormatUtils.formatAmount(goal.currentAmount)} / ${FormatUtils.formatAmount(goal.targetAmount)}", style = MaterialTheme.typography.bodySmall)
                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { editGoal = goal }, modifier = Modifier.fillMaxWidth()) { Text("Update Amount") }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var targetText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Savings Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Goal name") }, singleLine = true)
                    OutlinedTextField(value = targetText, onValueChange = { targetText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Target (KES)") }, prefix = { Text("KES ") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = {
                val target = ((targetText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                if (name.isNotBlank() && target > 0) { scope.launch { db.savingsGoalDao().insert(SavingsGoal(name = name, targetAmount = target)) }; showAddDialog = false }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    editGoal?.let { goal ->
        var amountText by remember { mutableStateOf(String.format("%.2f", goal.currentAmount / 100.0)) }
        AlertDialog(
            onDismissRequest = { editGoal = null },
            title = { Text("Update: ${goal.name}") },
            text = {
                OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Current amount (KES)") }, prefix = { Text("KES ") }, singleLine = true)
            },
            confirmButton = { TextButton(onClick = {
                val amt = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                scope.launch { db.savingsGoalDao().update(goal.copy(currentAmount = amt, isCompleted = amt >= goal.targetAmount)) }; editGoal = null
            }) { Text("Update") } },
            dismissButton = { TextButton(onClick = { editGoal = null }) { Text("Cancel") } }
        )
    }
}
