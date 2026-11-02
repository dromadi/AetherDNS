package com.security.aetherdns.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.security.aetherdns.AetherDnsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Mengaktifkan kembali proteksi setelah jeda berakhir. Juga dipanggil setelah boot / update aplikasi,
 * karena alarm sistem hilang pada kedua kejadian itu; tanpa jeda aktif, receiver langsung selesai.
 */
class PauseResumeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val repository = (context.applicationContext as AetherDnsApplication).dnsRepository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.resumeIfDue()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RESUME = "com.security.aetherdns.action.RESUME_PROTECTION"

        private val HANDLED_ACTIONS = setOf(
            ACTION_RESUME,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
