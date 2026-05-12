package com.personal.financetracker

import android.app.Application
import com.personal.financetracker.data.local.AppDatabase
import com.personal.financetracker.service.BudgetAlertWorker
import com.personal.financetracker.service.ConnectivitySyncTrigger
import com.personal.financetracker.service.DigestWorker
import com.personal.financetracker.service.SyncWorker

class FinanceTrackerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
        DigestWorker.schedule(this)
        BudgetAlertWorker.schedule(this)

        // Trigger an immediate sync as soon as the device sees an
        // internet-capable network. The 15-minute periodic worker is the
        // safety net — this is the fast path.
        ConnectivitySyncTrigger.register(this)
    }
}
