package com.personal.financetracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    data object Transactions : Screen("transactions", "History", Icons.Default.List)
    data object AddTransaction : Screen("add_transaction", "Add", Icons.Default.Add)
    data object Categories : Screen("categories", "Categories", Icons.Default.Menu)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Transactions,
    Screen.AddTransaction,
    Screen.Categories,
    Screen.Settings
)

// Sub-routes (accessible from dashboard/settings, no bottom nav)
object SubRoutes {
    const val BUDGETS = "budgets"
    const val SAVINGS = "savings"
    const val INVESTMENTS = "investments"
    const val DEBTS = "debts"

    const val TRANSACTION_DETAIL_PATTERN = "transaction/{id}"
    fun transactionDetail(id: Int) = "transaction/$id"
}
