package com.personal.financetracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.personal.financetracker.service.TransactionNotification
import com.personal.financetracker.ui.components.PermissionGate
import com.personal.financetracker.ui.navigation.AppNavigation
import com.personal.financetracker.ui.navigation.SubRoutes
import com.personal.financetracker.ui.theme.FinanceTrackerTheme

class MainActivity : ComponentActivity() {

    /**
     * Holds the next deep-link route to navigate to (if any). Set on launch
     * and on every new intent (the activity is single-top so a notification
     * tap while the app is already running reuses this instance).
     */
    private var pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = (application as FinanceTrackerApp).database
        pendingRoute.value = routeFromIntent(intent)

        setContent {
            FinanceTrackerTheme {
                PermissionGate {
                    val deepLink by remember { pendingRoute }
                    AppNavigation(
                        db = db,
                        pendingDeepLink = deepLink,
                        onDeepLinkConsumed = { pendingRoute.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A notification tap while the activity is already alive arrives here.
        setIntent(intent)
        routeFromIntent(intent)?.let { pendingRoute.value = it }
    }

    private fun routeFromIntent(intent: Intent?): String? {
        val id = intent?.getIntExtra(TransactionNotification.EXTRA_TRANSACTION_ID, -1) ?: -1
        return if (id >= 0) SubRoutes.transactionDetail(id) else null
    }
}
