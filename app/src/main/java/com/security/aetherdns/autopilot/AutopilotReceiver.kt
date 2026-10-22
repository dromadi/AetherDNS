package com.security.aetherdns.autopilot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.security.aetherdns.AetherDnsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Dibangunkan sistem saat jaringan berganti (PendingIntent dari `registerNetworkCallback`) dan oleh alarm cek
 * ulang Autopilot. Tidak diekspor: hanya PendingIntent milik aplikasi ini yang bisa memanggilnya.
 */
class AutopilotReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_NETWORK && action != ACTION_RECHECK) return
        val autopilot = (context.applicationContext as AetherDnsApplication).autopilot
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Jauh di bawah batas waktu broadcast latar; probe sendiri dibatasi timeout masing-masing
                withTimeoutOrNull(TIMEOUT_MS) { autopilot.evaluate(waitForNetwork = action == ACTION_NETWORK) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_NETWORK = "com.security.aetherdns.autopilot.NETWORK"
        const val ACTION_RECHECK = "com.security.aetherdns.autopilot.RECHECK"
        private const val TIMEOUT_MS = 45_000L
    }
}
