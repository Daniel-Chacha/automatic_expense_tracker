package com.personal.expensetracker

import android.app.Application
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.service.BudgetAlertWorker
import com.personal.expensetracker.service.DigestWorker
import com.personal.expensetracker.service.SyncWorker

class ExpenseTrackerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
        DigestWorker.schedule(this)
        BudgetAlertWorker.schedule(this)
    }
}
