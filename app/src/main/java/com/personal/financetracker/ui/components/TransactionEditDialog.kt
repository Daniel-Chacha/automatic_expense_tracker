package com.personal.financetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personal.financetracker.data.local.entity.Category
import com.personal.financetracker.data.local.entity.Transaction
import com.personal.financetracker.data.local.entity.TransactionStatus
import com.personal.financetracker.data.local.entity.TransactionType
import com.personal.financetracker.ui.theme.Expense
import com.personal.financetracker.ui.theme.Income

/**
 * Modal for viewing, editing, or deleting a single transaction.
 * The Save callback returns the updated transaction with isSynced flipped to false
 * so it re-pushes to Neon on the next sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditDialog(
    transaction: Transaction,
    expenseCategories: List<Category>,
    incomeCategories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit
) {
    var type by remember(transaction.id) { mutableStateOf(transaction.type) }
    var amountText by remember(transaction.id) {
        mutableStateOf(String.format("%.2f", transaction.amount / 100.0))
    }
    var description by remember(transaction.id) {
        mutableStateOf(transaction.description ?: "")
    }
    var selectedCategoryId by remember(transaction.id) {
        mutableIntStateOf(transaction.categoryId ?: -1)
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val categories = if (type == TransactionType.EXPENSE) expenseCategories else incomeCategories

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Type toggle
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { t ->
                        val color = if (t == TransactionType.INCOME) Income else Expense
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                type = t
                                if (categories.none { it.id == selectedCategoryId }) {
                                    selectedCategoryId = -1
                                }
                            },
                            label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(alpha = 0.2f),
                                selectedLabelColor = color
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (KES)") },
                    prefix = { Text("KES ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category", style = MaterialTheme.typography.labelMedium)
                CategoryPicker(
                    categories = categories,
                    selectedId = selectedCategoryId.takeIf { it >= 0 },
                    onSelect = { selectedCategoryId = it },
                    modifier = Modifier.heightIn(max = 220.dp)
                )

                if (confirmDelete) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Delete this transaction permanently?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row {
                                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { onDelete(transaction) }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                if (cents <= 0) return@TextButton
                onSave(
                    transaction.copy(
                        type = type,
                        amount = cents,
                        description = description.ifBlank { null },
                        categoryId = selectedCategoryId.takeIf { it >= 0 },
                        status = if (selectedCategoryId >= 0) TransactionStatus.CONFIRMED
                        else TransactionStatus.UNCATEGORIZED,
                        isSynced = false
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !confirmDelete
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
