package com.personal.financetracker.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private data class RuntimePermission(
    val id: String,
    val title: String,
    val rationale: String,
    val isGranted: (Context) -> Boolean,
    val request: () -> Unit
)

/**
 * Renders a list of missing permissions with rationale + grant buttons.
 * Falls through to [content] once every required permission is granted.
 */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    val smsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshTick++ }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }

    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    val permissions = remember(refreshTick) {
        buildList {
            add(
                RuntimePermission(
                    id = "sms",
                    title = "SMS access",
                    rationale = "Read M-Pesa and Airtel Money SMS to log transactions automatically.",
                    isGranted = { ctx ->
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                    },
                    request = {
                        smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                    }
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    RuntimePermission(
                        id = "notif",
                        title = "Notifications",
                        rationale = "Used for daily digest and budget overrun alerts.",
                        isGranted = { ctx ->
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        },
                        request = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                )
            }
            add(
                RuntimePermission(
                    id = "overlay",
                    title = "Overlay (system alert window)",
                    rationale = "Required to show the categorization popup when a transaction SMS arrives.",
                    isGranted = { ctx -> Settings.canDrawOverlays(ctx) },
                    request = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        overlayLauncher.launch(intent)
                    }
                )
            )
        }
    }

    val missing = permissions.filterNot { it.isGranted(context) }
    if (missing.isEmpty()) {
        content()
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Permissions needed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Grant the following so the app can do its job. Tap each row.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            missing.forEach { perm ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(perm.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            perm.rationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                        Button(onClick = perm.request, modifier = Modifier.align(Alignment.End)) {
                            Text("Grant")
                        }
                    }
                }
            }
        }
    }
}
