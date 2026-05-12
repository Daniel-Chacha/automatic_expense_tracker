package com.personal.financetracker.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

/**
 * Fires SyncWorker.syncNow() whenever the device gains an internet-capable
 * network. Registered for the lifetime of the app process from
 * [com.personal.financetracker.FinanceTrackerApp].
 */
object ConnectivitySyncTrigger {

    private const val TAG = "ConnectivitySync"
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available — triggering immediate sync")
                SyncWorker.syncNow(context.applicationContext)
            }
        })
        registered = true
    }
}
