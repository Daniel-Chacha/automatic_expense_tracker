package com.personal.financetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.personal.financetracker.data.local.entity.Transaction
import com.personal.financetracker.data.local.entity.TransactionType
import com.personal.financetracker.ui.components.TransactionCard
import com.personal.financetracker.ui.components.TransactionEditDialog
import com.personal.financetracker.ui.viewmodel.TransactionFilter
import com.personal.financetracker.ui.viewmodel.TransactionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onAddClick: () -> Unit = {}
) {
    val pagingItems = viewModel.pagedTransactions.collectAsLazyPagingItems()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val expenseCategories = remember(categories) { categories.filter { !it.isIncome } }
    val incomeCategories = remember(categories) { categories.filter { it.isIncome } }

    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search transactions...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransactionFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { viewModel.setFilter(f) },
                    label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (pagingItems.itemCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No transactions found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    count = pagingItems.itemCount,
                    key = { idx -> pagingItems[idx]?.id ?: idx }
                ) { idx ->
                    val txn = pagingItems[idx] ?: return@items
                    val cat = txn.categoryId?.let { categoryMap[it] }
                    TransactionCard(
                        transaction = txn,
                        categoryName = cat?.name,
                        categoryIcon = cat?.icon,
                        onClick = { editingTransaction = txn }
                    )
                }
            }
        }
    }

    editingTransaction?.let { txn ->
        TransactionEditDialog(
            transaction = txn,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            onDismiss = { editingTransaction = null },
            onSave = {
                viewModel.updateTransaction(it)
                editingTransaction = null
            },
            onDelete = {
                viewModel.deleteTransaction(it)
                editingTransaction = null
            }
        )
    }
}
