package com.security.aetherdns.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.security.aetherdns.AetherDnsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Menerima balasan kode dari notifikasi [PairingNotifier] dan menjalankan aktivasi (`goAsync`, tanpa service).
 * Tidak diekspor: hanya PendingIntent milik aplikasi ini yang bisa memicunya.
 */
class PairingCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(PairingNotifier.KEY_CODE)
            ?.toString()
            ?: return
        val app = context.applicationContext as AetherDnsApplication
        val pending = goAsync()
        scope.launch {
            try {
                app.wirelessActivator.activate(code)
                app.dnsRepository.refresh()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
