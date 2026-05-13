package com.personal.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.financetracker.data.HardDeletionService
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.data.local.entity.Category
import com.personal.financetracker.ui.components.DeleteConfirmDialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(db: AppDatabase) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
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
                        onDelete = { deletingCategory = category }
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
                    // Bump updatedAt so MetadataSyncRepository's watermark
                    // sees this row as modified and pushes it to Neon.
                    db.categoryDao().insert(
                        cat.copy(
                            name = name,
                            icon = icon,
                            color = color,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                editingCategory = null
            },
            onDelete = {
                deletingCategory = cat
                editingCategory = null
            }
        )
    }

    deletingCategory?.let { cat ->
        DeleteConfirmDialog(
            itemDescription = "the category \"${cat.name}\"",
            onDismiss = { deletingCategory = null },
            onConfirm = {
                scope.launch { HardDeletionService.deleteCategory(db, cat) }
                deletingCategory = null
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

private val PRESET_ICONS = listOf(
    // Food & drink
    "🍔","🍕","🍱","🥘","🍎","☕","🍷","🛒","🍰","🍫",
    // Transport
    "🚌","🚗","✈️","🚆","⛽","🚲","🛵","🛺",
    // Housing & utilities
    "🏠","🏢","💡","🔌","💧","🔥","📶","🛏",
    // Shopping & lifestyle
    "🛍","👕","👟","💄","🎁","💍","🧴",
    // Health & wellness
    "💊","🏥","🩺","💪","🧘","🦷",
    // Entertainment & hobbies
    "🎬","🎮","🎵","📺","🎤","🎸","⚽","🎨","📷","🎲","📖",
    // Tech
    "📱","💻","🖥","⌚",
    // Finance & work
    "💰","💵","💳","🏦","📈","📉","💼","📊",
    // Education
    "📚","🎓","✏️",
    // Family & pets
    "🐶","🐱","👶",
    // Travel
    "🏖","🗺","🏨","🧳",
    // Misc
    "📦","🎯","🔧","🛠","⭐","🎉","🔔","🏆","💎"
)

private val PRESET_COLORS = listOf(
    // Reds & oranges
    "#FF6B6B","#FF6B35","#E74C3C","#FF8C00","#FFA500",
    // Yellows
    "#F7DC6F","#FFD700","#FFC107",
    // Greens
    "#2ECC71","#4ECDC4","#96CEB4","#00B894","#4CAF50",
    // Blues
    "#45B7D1","#3498DB","#2196F3","#00BCD4","#1976D2",
    // Purples
    "#C44DFF","#9B59B6","#DDA0DD","#BA68C8",
    // Pinks
    "#FF69B4","#E91E63",
    // Browns & neutrals
    "#795548","#607D8B","#9E9E9E","#424242"
)

@OptIn(ExperimentalLayoutApi::class)
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
    var iconsExpanded by remember { mutableStateOf(true) }
    var colorsExpanded by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "New ${if (isIncome) "Income" else "Expense"} Category"
                else "Edit Category"
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SectionHeader(
                    label = "Icon",
                    currentPreview = { Text(icon, style = MaterialTheme.typography.titleMedium) },
                    expanded = iconsExpanded,
                    onToggle = { iconsExpanded = !iconsExpanded }
                )
                if (iconsExpanded) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PRESET_ICONS.forEach { emoji ->
                            IconCell(emoji = emoji, selected = icon == emoji, onClick = { icon = emoji })
                        }
                    }
                }

                SectionHeader(
                    label = "Color",
                    currentPreview = {
                        val c = try { Color(android.graphics.Color.parseColor(color)) }
                                catch (_: Exception) { Color.Gray }
                        Box(Modifier.size(20.dp).clip(CircleShape).background(c))
                    },
                    expanded = colorsExpanded,
                    onToggle = { colorsExpanded = !colorsExpanded }
                )
                if (colorsExpanded) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PRESET_COLORS.forEach { hex ->
                            ColorCell(hex = hex, selected = color == hex, onClick = { color = hex })
                        }
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

@Composable
private fun SectionHeader(
    label: String,
    currentPreview: @Composable () -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        currentPreview()
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Hide" else "Show"
        )
    }
}

@Composable
private fun IconCell(emoji: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ColorCell(hex: String, selected: Boolean, onClick: () -> Unit) {
    val c = try { Color(android.graphics.Color.parseColor(hex)) }
            catch (_: Exception) { Color.Gray }
    val borderColor = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(c)
            .border(3.dp, borderColor, CircleShape)
            .clickable { onClick() }
    )
}
