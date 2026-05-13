package com.personal.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.personal.financetracker.ui.components.TransactionCard
import com.personal.financetracker.ui.viewmodel.TransactionFilter
import com.personal.financetracker.ui.viewmodel.TransactionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onAddClick: () -> Unit = {},
    onTransactionClick: (Int) -> Unit = {}
) {
    val pagingItems = viewModel.pagedTransactions.collectAsLazyPagingItems()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val availableYears by viewModel.availableYears.collectAsStateWithLifecycle()

    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    var yearMenuExpanded by remember { mutableStateOf(false) }
    val monthLabels = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Year + Month nav ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { viewModel.previousMonth() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
            }
            Text(
                monthLabels[selectedMonth],
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { viewModel.nextMonth() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
            }
            Box {
                AssistChip(
                    onClick = { yearMenuExpanded = true },
                    label = { Text(selectedYear.toString()) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                )
                DropdownMenu(expanded = yearMenuExpanded, onDismissRequest = { yearMenuExpanded = false }) {
                    availableYears.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year.toString()) },
                            onClick = {
                                viewModel.setYear(year)
                                yearMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Month chip row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(12) { idx ->
                val isSelected = idx == selectedMonth
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setMonth(idx) },
                    label = { Text(monthLabels[idx]) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search transactions...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
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

        Spacer(modifier = Modifier.height(4.dp))

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
                        text = "No transactions for ${monthLabels[selectedMonth]} $selectedYear",
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
                        onClick = { onTransactionClick(txn.id) }
                    )
                }
            }
        }
    }
}
