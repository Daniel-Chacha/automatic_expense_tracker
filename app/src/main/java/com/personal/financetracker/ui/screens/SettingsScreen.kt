package com.personal.financetracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.financetracker.BuildConfig
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.Account
import com.personal.financetracker.data.local.entity.SmsSource
import com.personal.financetracker.service.SyncWorker
import com.personal.financetracker.ui.theme.Expense
import com.personal.financetracker.ui.theme.Income
import com.personal.financetracker.ui.theme.Warning
import com.personal.financetracker.util.CsvExporter
import com.personal.financetracker.util.FormatUtils
import com.personal.financetracker.util.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by db.accountDao().getAll().collectAsStateWithLifecycle(emptyList())
    val smsSources by db.smsSourceDao().getAll().collectAsStateWithLifecycle(emptyList())
    val unsyncedCount by db.transactionDao().getUnsyncedCountFlow().collectAsStateWithLifecycle(0)
    val syncSnapshot by SyncState.observe(context).collectAsStateWithLifecycle(SyncState.read(context))

    var showAddAccount by remember { mutableStateOf(false) }
    var editAccount by remember { mutableStateOf<Account?>(null) }
    var showAddSms by remember { mutableStateOf(false) }
    var editSms by remember { mutableStateOf<SmsSource?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SyncStatusCard(snapshot = syncSnapshot, pending = unsyncedCount) }

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
                    IconButton(onClick = { editAccount = acc }) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                    IconButton(onClick = { scope.launch { db.accountDao().delete(acc) } }) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error.copy(0.7f))
                    }
                }
            }
        }

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
                    IconButton(onClick = { editSms = src }) { Icon(Icons.Default.Edit, "Edit") }
                    IconButton(onClick = { scope.launch { db.smsSourceDao().delete(src) } }) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error.copy(0.7f))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { Text("Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

        item {
            OutlinedButton(
                onClick = { showExportDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("📤 Export Data") }
        }

        item {
            OutlinedButton(onClick = {
                SyncWorker.syncNow(context)
                Toast.makeText(context, "Sync triggered", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("🔄 Sync Now") }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Text(
                "Finance Tracker v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showAddAccount) {
        AccountDialog(
            initial = null,
            onDismiss = { showAddAccount = false },
            onSave = { name, balance ->
                scope.launch { db.accountDao().insert(Account(name = name, balance = balance)) }
                showAddAccount = false
            }
        )
    }

    editAccount?.let { acc ->
        AccountDialog(
            initial = acc,
            onDismiss = { editAccount = null },
            onSave = { name, balance ->
                scope.launch { db.accountDao().insert(acc.copy(name = name, balance = balance)) }
                editAccount = null
            }
        )
    }

    if (showAddSms) {
        SmsSourceDialog(
            initial = null,
            onDismiss = { showAddSms = false },
            onSave = { sender, label ->
                scope.launch { db.smsSourceDao().insert(SmsSource(sender = sender, label = label)) }
                showAddSms = false
            }
        )
    }

    editSms?.let { src ->
        SmsSourceDialog(
            initial = src,
            onDismiss = { editSms = null },
            onSave = { sender, label ->
                scope.launch { db.smsSourceDao().insert(src.copy(sender = sender, label = label)) }
                editSms = null
            }
        )
    }

    if (showExportDialog) {
        ExportDataDialog(
            onDismiss = { showExportDialog = false },
            onExport = { unit, count ->
                scope.launch {
                    val cutoff = CsvExporter.cutoffMillis(unit, count)
                    val all = db.transactionDao().getAll().first().filter { it.transactedAt >= cutoff }
                    val cats = db.categoryDao().getAll().first()
                    val accs = db.accountDao().getAll().first()
                    val budgets = db.budgetDao().getByMonth(0L).first()
                        .ifEmpty { db.budgetDao().getByMonth(System.currentTimeMillis()).first() }
                    val savings = db.savingsGoalDao().getAll().first().filter { it.createdAt >= cutoff }
                    val investments = db.investmentDao().getAll().first()
                        .filter { it.createdAt >= cutoff }
                    val debts = db.debtDao().getAll().first().filter { it.createdAt >= cutoff }

                    val payload = CsvExporter.ExportPayload(
                        transactions = all,
                        categories = cats,
                        accounts = accs,
                        budgets = budgets,
                        savingsGoals = savings,
                        investments = investments,
                        debts = debts
                    )
                    val csv = CsvExporter.buildAll(payload)
                    val filename = "expense-data-${FormatUtils.formatDate(System.currentTimeMillis()).replace("/", "-")}.csv"
                    CsvExporter.shareAsCsv(context, csv, filename)
                }
                showExportDialog = false
            }
        )
    }
}

@Composable
private fun SyncStatusCard(snapshot: SyncState.Snapshot, pending: Int) {
    val (dotColor, headline) = when (snapshot.outcome) {
        SyncState.Outcome.NEVER -> Color.Gray to "Not synced yet"
        SyncState.Outcome.SUCCESS -> if (pending > 0) Warning to "Synced — $pending pending" else Income to "All synced"
        SyncState.Outcome.TRANSIENT_FAILURE -> Warning to "Sync retrying"
        SyncState.Outcome.PERMANENT_FAILURE -> Expense to "Sync failed"
    }
    val subline = buildString {
        snapshot.lastSyncAt?.let { append("Last attempt ${FormatUtils.formatRelative(it)}") }
        if (snapshot.outcome == SyncState.Outcome.SUCCESS && snapshot.lastCount > 0) {
            if (isNotEmpty()) append(" • ")
            append("${snapshot.lastCount} pushed")
        }
        snapshot.lastReason?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(" • ")
            append(it)
        }
        if (isEmpty()) append("Triggered automatically when on Wi-Fi/data")
    }

    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Neon sync", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                Text(headline, fontWeight = FontWeight.Medium)
                Text(subline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        }
    }
}

@Composable
private fun AccountDialog(
    initial: Account?,
    onDismiss: () -> Unit,
    onSave: (name: String, balanceCents: Int) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var balanceText by remember {
        mutableStateOf(initial?.let { String.format("%.2f", it.balance / 100.0) } ?: "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Account" else "Edit Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. M-Pesa, Airtel Money)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Balance (KES)") },
                    prefix = { Text("KES ") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val cents = ((balanceText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                    onSave(name, cents)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SmsSourceDialog(
    initial: SmsSource?,
    onDismiss: () -> Unit,
    onSave: (sender: String, label: String?) -> Unit
) {
    var sender by remember { mutableStateOf(initial?.sender ?: "") }
    var label by remember { mutableStateOf(initial?.label ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New SMS Source" else "Edit SMS Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("Sender (MPESA, AIRTELMONEY)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g. Safaricom M-Pesa)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (sender.isNotBlank()) onSave(sender, label.ifBlank { null })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDataDialog(
    onDismiss: () -> Unit,
    onExport: (CsvExporter.DurationUnit, Int) -> Unit
) {
    var unit by remember { mutableStateOf(CsvExporter.DurationUnit.MONTHS) }
    var countText by remember { mutableStateOf("3") }
    var unitMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Pick a window. The export covers transactions, categories, accounts, budgets, savings goals, investments, and debts.",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { v -> countText = v.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Count") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = unit != CsvExporter.DurationUnit.ALL,
                        modifier = Modifier.weight(1f)
                    )

                    ExposedDropdownMenuBox(
                        expanded = unitMenuOpen,
                        onExpandedChange = { unitMenuOpen = !unitMenuOpen },
                        modifier = Modifier.weight(1.4f)
                    ) {
                        OutlinedTextField(
                            value = unit.label(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = unitMenuOpen,
                            onDismissRequest = { unitMenuOpen = false }
                        ) {
                            CsvExporter.DurationUnit.entries.forEach { entry ->
                                DropdownMenuItem(
                                    text = { Text(entry.label()) },
                                    onClick = {
                                        unit = entry
                                        unitMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = countText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                onExport(unit, n)
            }) { Text("Export") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun CsvExporter.DurationUnit.label(): String = when (this) {
    CsvExporter.DurationUnit.WEEKS -> "Weeks"
    CsvExporter.DurationUnit.MONTHS -> "Months"
    CsvExporter.DurationUnit.YEARS -> "Years"
    CsvExporter.DurationUnit.ALL -> "All time"
}
