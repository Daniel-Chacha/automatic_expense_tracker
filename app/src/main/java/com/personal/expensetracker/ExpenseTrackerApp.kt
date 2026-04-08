package com.personal.expensetracker

import android.app.Application
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.service.DigestWorker
import com.personal.expensetracker.service.SyncWorker

class ExpenseTrackerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        // Schedule periodic Supabase sync (every 15 min when connected)
        SyncWorker.schedule(this)
        // Schedule daily digest notification (8 PM)
        DigestWorker.schedule(this)
    }
}
