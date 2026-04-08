package com.personal.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.ui.navigation.AppNavigation
import com.personal.expensetracker.ui.theme.ExpenseTrackerTheme
import com.personal.expensetracker.util.BiometricHelper

class MainActivity : ComponentActivity() {

    private var isAuthenticated by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = (application as ExpenseTrackerApp).database

        setContent {
            ExpenseTrackerTheme {
                AnimatedVisibility(
                    visible = isAuthenticated,
                    enter = fadeIn()
                ) {
                    AppNavigation(db = db)
                }

                if (!isAuthenticated) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔒", style = MaterialTheme.typography.displayLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Expense Tracker",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Authenticate to continue",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { promptBiometric() }) {
                                    Text("Unlock")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Auto-prompt on launch
        promptBiometric()
    }

    private fun promptBiometric() {
        if (BiometricHelper.canAuthenticate(this)) {
            BiometricHelper.authenticate(
                activity = this,
                onSuccess = { isAuthenticated = true },
                onError = { /* User can tap Unlock to retry */ }
            )
        } else {
            // No biometric/credential set up — skip lock
            isAuthenticated = true
        }
    }
}
