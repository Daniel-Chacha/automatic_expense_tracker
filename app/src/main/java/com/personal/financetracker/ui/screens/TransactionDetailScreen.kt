package com.personal.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.financetracker.data.local.entity.TransactionStatus
import com.personal.financetracker.data.local.entity.TransactionType
import com.personal.financetracker.ui.components.CategoryPicker
import com.personal.financetracker.ui.components.DeleteConfirmDialog
import com.personal.financetracker.ui.theme.Expense
import com.personal.financetracker.ui.theme.Income
import com.personal.financetracker.ui.viewmodel.TransactionsViewModel
import com.personal.financetracker.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: Int,
    viewModel: TransactionsViewModel,
    onBack: () -> Unit
) {
    val transaction by viewModel.getById(transactionId).collectAsStateWithLifecycle(initialValue = null)
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    var editing by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableIntStateOf(-1) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Seed local edit state when entering edit mode for the first time
    LaunchedEffect(transaction?.id, editing) {
        if (editing && transaction != null) {
            val t = transaction!!
            amountText = String.format("%.2f", t.amount / 100.0)
            description = t.description.orEmpty()
            selectedCategoryId = t.categoryId ?: -1
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Edit Transaction" else "Transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (transaction != null && !editing) {
                        IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        val txn = transaction
        if (txn == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Transaction not found", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Amount hero
            val isIncome = txn.type == TransactionType.INCOME
            val color = if (isIncome) Income else Expense
            val sign = if (isIncome) "+" else "-"
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (isIncome) "Income" else "Expense", style = MaterialTheme.typography.labelMedium, color = color.copy(0.8f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$sign ${FormatUtils.formatAmount(txn.amount)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${FormatUtils.formatDate(txn.transactedAt)} • ${FormatUtils.formatTime(txn.transactedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                }
            }

            if (editing) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (KES)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("KES ") }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
                Text("Category", style = MaterialTheme.typography.titleSmall)
                CategoryPicker(
                    categories = categories.filter { it.isIncome == isIncome },
                    selectedId = if (selectedCategoryId >= 0) selectedCategoryId else null,
                    onSelect = { selectedCategoryId = it },
                    modifier = Modifier.heightIn(max = 280.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { editing = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            val amount = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                            if (amount > 0) {
                                viewModel.updateTransaction(
                                    txn.copy(
                                        amount = amount,
                                        description = description.ifBlank { null },
                                        categoryId = if (selectedCategoryId >= 0) selectedCategoryId else null,
                                        status = if (selectedCategoryId >= 0) TransactionStatus.CONFIRMED
                                                 else TransactionStatus.UNCATEGORIZED
                                    )
                                )
                                editing = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }
                }
            } else {
                DetailRow("Description", txn.description ?: "—")
                DetailRow("Category", categoryMap[txn.categoryId]?.let { "${it.icon ?: ""} ${it.name}" } ?: "Uncategorized")
                DetailRow("Status", txn.status.name.lowercase().replaceFirstChar { it.uppercase() })
                txn.counterparty?.let { DetailRow("Counterparty", it) }
                txn.reference?.let { DetailRow("Reference", it) }
                DetailRow("Recorded", FormatUtils.formatDate(txn.createdAt))
                DetailRow(
                    "Sync",
                    if (txn.isSynced) "Synced to cloud" else "Pending sync${if (txn.syncFailures > 0) " (${txn.syncFailures} fails)" else ""}"
                )
                if (!txn.meta.isNullOrBlank()) {
                    RawSmsSection(meta = txn.meta!!)
                }
            }
        }

        if (showDeleteConfirm) {
            DeleteConfirmDialog(
                itemDescription = "this transaction (${FormatUtils.formatAmount(txn.amount)})",
                onDismiss = { showDeleteConfirm = false },
                onConfirm = {
                    viewModel.deleteTransaction(txn)
                    showDeleteConfirm = false
                    onBack()
                }
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RawSmsSection(meta: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Text("Source data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Spacer(Modifier.height(2.dp))
        Text(
            if (expanded) meta else "Tap to view raw SMS / source JSON",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.8f),
            maxLines = if (expanded) Int.MAX_VALUE else 1
        )
    }
}
