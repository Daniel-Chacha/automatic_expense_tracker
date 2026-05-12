package com.personal.financetracker.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.ui.screens.*
import com.personal.financetracker.ui.viewmodel.*

@Composable
fun AppNavigation(db: AppDatabase) {
    val navController = rememberNavController()
    val currentRoute by navController.currentBackStackEntryAsState()
    val currentScreen = currentRoute?.destination?.route

    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(db)
    )
    val transactionsViewModel: TransactionsViewModel = viewModel(
        factory = TransactionsViewModelFactory(db)
    )

    // Hide bottom bar on detail screens
    val showBottomBar = currentScreen in bottomNavScreens.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, screen.title) },
                            label = { Text(screen.title) },
                            selected = currentScreen == screen.route,
                            onClick = {
                                if (currentScreen != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Dashboard.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ── Bottom Nav Screens ──
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }

            composable(Screen.Transactions.route) {
                TransactionsScreen(
                    viewModel = transactionsViewModel,
                    onAddClick = { navController.navigate(Screen.AddTransaction.route) }
                )
            }

            composable(Screen.AddTransaction.route) {
                AddTransactionScreen(db = db, onBack = { navController.popBackStack() })
            }

            composable(Screen.Categories.route) {
                CategoriesScreen(db = db)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(db = db)
            }

            // ── Sub-route Screens ──
            composable(SubRoutes.BUDGETS) {
                BudgetScreen(db = db, onBack = { navController.popBackStack() })
            }

            composable(SubRoutes.SAVINGS) {
                SavingsScreen(db = db, onBack = { navController.popBackStack() })
            }

            composable(SubRoutes.INVESTMENTS) {
                InvestmentsScreen(db = db, onBack = { navController.popBackStack() })
            }

            composable(SubRoutes.DEBTS) {
                DebtScreen(db = db, onBack = { navController.popBackStack() })
            }
        }
    }
}
