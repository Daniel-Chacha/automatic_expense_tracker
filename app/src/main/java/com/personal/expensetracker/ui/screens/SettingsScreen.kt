package com.personal.expensetracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.entity.Account
import com.personal.expensetracker.data.local.entity.SmsSource
import com.personal.expensetracker.service.SyncWorker
import com.personal.expensetracker.util.CsvExporter
import com.personal.expensetracker.util.FormatUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by db.accountDao().getAll().collectAsStateWithLifecycle(emptyList())
    val smsSources by db.smsSourceDao().getAll().collectAsStateWithLifecycle(emptyList())

    var showAddAccount by remember { mutableStateOf(false) }
    var showAddSms by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Accounts ──
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Accounts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddAccount = true }) { Icon(Icons.Default.Add, "Add account") }
            }
        }
        items(accounts, key = { it.id }) { acc ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(acc.name, fontWeight = FontWeight.Medium)
                        Text("Balance: ${FormatUtils.formatAmount(acc.balance)}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { scope.launch { db.accountDao().delete(acc) } }) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error.copy(0.7f))
                    }
                }
            }
        }

        // ── SMS Sources ──
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SMS Sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddSms = true }) { Icon(Icons.Default.Add, "Add source") }
            }
        }
        items(smsSources, key = { it.id }) { src ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(src.sender, fontWeight = FontWeight.Medium)
                        Text(src.label ?: "Unknown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                    Switch(checked = src.isActive, onCheckedChange = { active ->
                        scope.launch { db.smsSourceDao().insert(src.copy(isActive = active)) }
                    })
                }
            }
        }

        // ── Actions ──
        item { Spacer(Modifier.height(8.dp)) }
        item { Text("Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

        item {
            OutlinedButton(onClick = {
                scope.launch {
                    val txns = db.transactionDao().getAll().first()
                    val cats = db.categoryDao().getAll().first().associateBy { it.id }
                    val csv = CsvExporter.export(txns, cats)
                    CsvExporter.shareAsCsv(context, csv)
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("📤 Export Transactions to CSV") }
        }

        item {
            OutlinedButton(onClick = {
                SyncWorker.syncNow(context)
                Toast.makeText(context, "Sync triggered", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("🔄 Sync Now") }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Text("Expense Tracker v1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), modifier = Modifier.fillMaxWidth())
        }
    }

    // ── Add Account Dialog ──
    if (showAddAccount) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddAccount = false },
            title = { Text("New Account") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. M-Pesa, KCB)") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { scope.launch { db.accountDao().insert(Account(name = name)) }; showAddAccount = false } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showAddAccount = false }) { Text("Cancel") } }
        )
    }

    // ── Add SMS Source Dialog ──
    if (showAddSms) {
        var sender by remember { mutableStateOf("") }
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSms = false },
            title = { Text("New SMS Source") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sender, onValueChange = { sender = it }, label = { Text("Sender (e.g. MPESA)") }, singleLine = true)
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label (e.g. Safaricom M-Pesa)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { if (sender.isNotBlank()) { scope.launch { db.smsSourceDao().insert(SmsSource(sender = sender, label = label.ifBlank { null })) }; showAddSms = false } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showAddSms = false }) { Text("Cancel") } }
        )
    }
}
