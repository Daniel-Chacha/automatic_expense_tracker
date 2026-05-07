package com.personal.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.personal.expensetracker.ui.components.PermissionGate
import com.personal.expensetracker.ui.navigation.AppNavigation
import com.personal.expensetracker.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = (application as ExpenseTrackerApp).database

        setContent {
            ExpenseTrackerTheme {
                PermissionGate {
                    AppNavigation(db = db)
                }
            }
        }
    }
}
