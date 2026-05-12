package com.personal.financetracker.ui.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.TransactionType
import com.personal.financetracker.ui.components.CategoryPicker
import com.personal.financetracker.ui.theme.Expense
import com.personal.financetracker.ui.theme.Income
import com.personal.financetracker.util.FormatUtils

@Composable
fun TransactionPopup(
    amount: Int,
    type: String,
    counterparty: String?,
    db: AppDatabase,
    onConfirm: (categoryId: Int, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val isIncome = type == TransactionType.INCOME.name
    val categories by db.categoryDao().getByType(isIncome)
        .collectAsStateWithLifecycle(emptyList())

    var selectedCategoryId by remember { mutableIntStateOf(-1) }
    var note by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ──
            Text(
                text = if (isIncome) "Money Received" else "Money Spent",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            // ── Amount ──
            Text(
                text = "${if (isIncome) "+" else "-"} ${FormatUtils.formatAmount(amount)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) Income else Expense
            )

            // ── Counterparty ──
            counterparty?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            HorizontalDivider()

            // ── Category Picker ──
            Text("Select Category", style = MaterialTheme.typography.labelLarge)

            CategoryPicker(
                categories = categories,
                selectedId = if (selectedCategoryId >= 0) selectedCategoryId else null,
                onSelect = { selectedCategoryId = it },
                modifier = Modifier.heightIn(max = 200.dp)
            )

            // ── Note ──
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("Add a note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // ── Actions ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Later")
                }
                Button(
                    onClick = {
                        if (selectedCategoryId >= 0) {
                            onConfirm(selectedCategoryId, note)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedCategoryId >= 0
                ) {
                    Text("Save")
                }
            }
        }
    }
}
