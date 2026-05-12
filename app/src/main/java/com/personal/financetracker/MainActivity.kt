package com.personal.financetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.personal.financetracker.ui.components.PermissionGate
import com.personal.financetracker.ui.navigation.AppNavigation
import com.personal.financetracker.ui.theme.FinanceTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = (application as FinanceTrackerApp).database

        setContent {
            FinanceTrackerTheme {
                PermissionGate {
                    AppNavigation(db = db)
                }
            }
        }
    }
}
