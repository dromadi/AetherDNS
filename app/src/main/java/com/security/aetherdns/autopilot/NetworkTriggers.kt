package com.security.aetherdns.autopilot

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

/**
 * Callback jaringan berbasis PendingIntent: sistem membangunkan [AutopilotReceiver] setiap jaringan berganti,
 * tanpa proses yang berjalan terus. Dipakai bersama oleh Autopilot dan Aturan Pintar. Pendaftaran hilang saat
 * reboot / update, jadi [sync] dipanggil setiap proses mulai; PendingIntent yang sama menggantikan yang lama.
 */
object NetworkTriggers {
    private const val REQUEST_NETWORK = 41

    fun sync(context: Context, needed: Boolean) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            if (needed) {
                val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
                connectivity.registerNetworkCallback(request, intent(context))
            } else {
                connectivity.unregisterNetworkCallback(intent(context))
            }
        }
    }

    private fun intent(context: Context): PendingIntent {
        // Mutable: sistem menambahkan extra Network; intent eksplisit ke receiver yang tidak diekspor
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(context, AutopilotReceiver::class.java).setAction(AutopilotReceiver.ACTION_NETWORK)
        return PendingIntent.getBroadcast(context, REQUEST_NETWORK, intent, flags)
    }
}
