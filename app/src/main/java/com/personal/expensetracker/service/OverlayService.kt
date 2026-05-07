package com.personal.expensetracker.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.entity.TransactionStatus
import com.personal.expensetracker.ui.overlay.TransactionPopup
import com.personal.expensetracker.ui.theme.ExpenseTrackerTheme
import com.personal.expensetracker.util.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Draws a floating overlay popup over any app for transaction categorization.
 * Requires SYSTEM_ALERT_WINDOW permission.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val lifecycleOwner = ServiceLifecycleOwner()
    private var timeoutJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val transactionId = intent?.getIntExtra(EXTRA_TRANSACTION_ID, -1) ?: -1
        val amount = intent?.getIntExtra(EXTRA_AMOUNT, 0) ?: 0
        val type = intent?.getStringExtra(EXTRA_TYPE) ?: "EXPENSE"
        val counterparty = intent?.getStringExtra(EXTRA_COUNTERPARTY)

        if (transactionId >= 0) {
            showOverlay(transactionId, amount, type, counterparty)
        }
        return START_NOT_STICKY
    }

    private fun showOverlay(transactionId: Int, amount: Int, type: String, counterparty: String?) {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val view = ComposeView(this).also {
            it.setViewTreeLifecycleOwner(lifecycleOwner)
            it.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }

        val db = AppDatabase.getInstance(this)

        view.setContent {
            ExpenseTrackerTheme {
                TransactionPopup(
                    amount = amount,
                    type = type,
                    counterparty = counterparty,
                    db = db,
                    onConfirm = { categoryId, note ->
                        serviceScope.launch {
                            val txn = db.transactionDao().getAll().first()
                                .find { it.id == transactionId }
                            txn?.let {
                                db.transactionDao().update(
                                    it.copy(
                                        categoryId = categoryId,
                                        description = note.ifBlank { it.description },
                                        status = TransactionStatus.CONFIRMED
                                    )
                                )
                            }
                            dismissOverlay()
                        }
                    },
                    onDismiss = { dismissOverlay() }
                )
            }
        }

        overlayView = view
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager.addView(view, params)

        timeoutJob?.cancel()
        timeoutJob = serviceScope.launch {
            delay(AppConfig.OVERLAY_TIMEOUT_SECONDS * 1000L)
            // User did not interact — leave the transaction as UNCATEGORIZED
            // (already its initial state) and fade the overlay away.
            dismissOverlay()
        }
    }

    private fun dismissOverlay() {
        timeoutJob?.cancel()
        timeoutJob = null
        overlayView?.let {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            windowManager.removeView(it)
            overlayView = null
        }
        stopSelf()
    }

    override fun onDestroy() {
        dismissOverlay()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_TYPE = "type"
        const val EXTRA_COUNTERPARTY = "counterparty"
    }
}

/**
 * Minimal LifecycleOwner for hosting ComposeView inside a Service.
 */
internal class ServiceLifecycleOwner :
    LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_CREATE) {
            savedStateRegistryController.performRestore(null)
        }
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
