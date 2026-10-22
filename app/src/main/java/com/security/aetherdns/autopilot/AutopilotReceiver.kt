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
 * Dibangunkan sistem saat jaringan berganti (PendingIntent dari [NetworkTriggers]), oleh alarm cek ulang
 * Autopilot, dan oleh alarm batas jadwal Aturan Pintar. Tidak diekspor: hanya PendingIntent milik aplikasi ini yang bisa memanggilnya.
 */
class AutopilotReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_NETWORK && action != ACTION_RECHECK && action != ACTION_RULES) return
        val app = context.applicationContext as AetherDnsApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Jauh di bawah batas waktu broadcast latar; probe sendiri dibatasi timeout masing-masing
                withTimeoutOrNull(TIMEOUT_MS) { app.evaluateNetwork(waitForNetwork = action == ACTION_NETWORK) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_NETWORK = "com.security.aetherdns.autopilot.NETWORK"
        const val ACTION_RECHECK = "com.security.aetherdns.autopilot.RECHECK"
        const val ACTION_RULES = "com.security.aetherdns.rules.BOUNDARY"
        private const val TIMEOUT_MS = 45_000L
    }
}
