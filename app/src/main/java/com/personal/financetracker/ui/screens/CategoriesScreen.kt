package com.personal.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.Category
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(db: AppDatabase) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    val scope = rememberCoroutineScope()

    val expenseCategories by db.categoryDao().getByType(false).collectAsStateWithLifecycle(emptyList())
    val incomeCategories by db.categoryDao().getByType(true).collectAsStateWithLifecycle(emptyList())
    val categories = if (selectedTab == 0) expenseCategories else incomeCategories

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add category")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Expense") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Income") }
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    CategoryItem(
                        category = category,
                        onClick = { editingCategory = category },
                        onDelete = { scope.launch { db.categoryDao().delete(category) } }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryDialog(
            initial = null,
            isIncome = selectedTab == 1,
            onDismiss = { showAddDialog = false },
            onSave = { name, icon, color ->
                scope.launch {
                    db.categoryDao().insert(
                        Category(
                            name = name,
                            icon = icon,
                            color = color,
                            isIncome = selectedTab == 1
                        )
                    )
                }
                showAddDialog = false
            },
            onDelete = null
        )
    }

    editingCategory?.let { cat ->
        CategoryDialog(
            initial = cat,
            isIncome = cat.isIncome,
            onDismiss = { editingCategory = null },
            onSave = { name, icon, color ->
                scope.launch {
                    db.categoryDao().insert(cat.copy(name = name, icon = icon, color = color))
                }
                editingCategory = null
            },
            onDelete = {
                scope.launch { db.categoryDao().delete(cat) }
                editingCategory = null
            }
        )
    }
}

@Composable
private fun CategoryItem(
    category: Category,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        category.color?.let {
                            try { Color(android.graphics.Color.parseColor(it)).copy(alpha = 0.2f) }
                            catch (_: Exception) { MaterialTheme.colorScheme.primaryContainer }
                        } ?: MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(category.icon ?: "📦")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onClick) {
                Icon(Icons.Default.Edit, "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun CategoryDialog(
    initial: Category?,
    isIncome: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, color: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "📦") }
    var color by remember { mutableStateOf(initial?.color ?: "#4ECDC4") }

    val presetIcons = listOf("🍔","🚌","🏠","💡","🛍","💊","🎬","📚","📱","💰","💻","📥","🏦","💵","✈️","🎮")
    val presetColors = listOf("#FF6B35","#4ECDC4","#45B7D1","#96CEB4","#DDA0DD","#FF6B6B","#C44DFF","#F7DC6F","#2ECC71","#E67E22")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "New ${if (isIncome) "Income" else "Expense"} Category"
                else "Edit Category"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Icon", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    presetIcons.take(8).forEach { emoji ->
                        FilterChip(
                            selected = icon == emoji,
                            onClick = { icon = emoji },
                            label = { Text(emoji) }
                        )
                    }
                }

                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    presetColors.take(6).forEach { hex ->
                        val c = Color(android.graphics.Color.parseColor(hex))
                        FilterChip(
                            selected = color == hex,
                            onClick = { color = hex },
                            label = { Text("") },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name, icon, color) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
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
