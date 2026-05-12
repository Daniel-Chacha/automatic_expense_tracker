package com.personal.financetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.Transaction
import com.personal.financetracker.data.local.entity.TransactionStatus
import com.personal.financetracker.data.local.entity.TransactionType
import com.personal.financetracker.ui.components.CategoryPicker
import com.personal.financetracker.ui.theme.Expense
import com.personal.financetracker.ui.theme.Income
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    db: AppDatabase,
    onBack: () -> Unit
) {
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableIntStateOf(-1) }
    var description by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val expenseCategories by db.categoryDao().getByType(false).collectAsStateWithLifecycle(emptyList())
    val incomeCategories by db.categoryDao().getByType(true).collectAsStateWithLifecycle(emptyList())
    val categories = if (type == TransactionType.EXPENSE) expenseCategories else incomeCategories

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Type Toggle ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TransactionType.entries.forEach { t ->
                    val isSelected = type == t
                    val color = if (t == TransactionType.INCOME) Income else Expense
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            type = t
                            selectedCategoryId = -1
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

            // ── Amount ──
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount (KES)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                prefix = { Text("KES ") }
            )

            // ── Description ──
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ── Category Picker ──
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleSmall
            )

            CategoryPicker(
                categories = categories,
                selectedId = if (selectedCategoryId >= 0) selectedCategoryId else null,
                onSelect = { selectedCategoryId = it },
                modifier = Modifier.heightIn(max = 300.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Save Button ──
            Button(
                onClick = {
                    val amount = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toInt()
                    if (amount <= 0) return@Button

                    scope.launch {
                        db.transactionDao().insert(
                            Transaction(
                                type = type,
                                status = if (selectedCategoryId >= 0) TransactionStatus.CONFIRMED
                                else TransactionStatus.UNCATEGORIZED,
                                amount = amount,
                                categoryId = if (selectedCategoryId >= 0) selectedCategoryId else null,
                                description = description.ifBlank { null },
                                transactedAt = System.currentTimeMillis()
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = amountText.isNotBlank()
            ) {
                Text("Save Transaction")
            }
        }
    }
}
