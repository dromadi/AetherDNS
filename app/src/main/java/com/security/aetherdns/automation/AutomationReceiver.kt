package com.security.aetherdns.automation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.security.aetherdns.AetherDnsApplication
import com.security.aetherdns.data.ApplyResult
import com.security.aetherdns.data.PauseResult
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.ui.displayName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Penerima perintah dari Tasker / MacroDroid / Automate. Nonaktif secara default (lihat [AutomationSettings])
 * dan setiap perintah wajib membawa token rahasia. Broadcast harus eksplisit ke paket aplikasi ini
 * (Android 8+ tidak mengirim broadcast implisit ke receiver di manifest).
 *
 * Hasil dikembalikan sebagai result code bila broadcast dikirim secara ordered:
 * [Activity.RESULT_OK] berhasil, [Activity.RESULT_CANCELED] gagal / ditolak.
 */
class AutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AetherDnsApplication
        val parsed = AutomationProtocol.parse(
            action = intent.action,
            extras = intent.stringExtras(),
            expectedToken = app.automationSettings.token(),
            profiles = DefaultProfiles.list + app.profileRepository.profiles.value,
            profileName = { it.displayName(context) }
        )

        val command = when (parsed) {
            is AutomationProtocol.Result.Rejected -> {
                Log.w(TAG, "Perintah otomasi ditolak: ${parsed.reason}")
                if (isOrderedBroadcast) resultCode = Activity.RESULT_CANCELED
                return
            }
            is AutomationProtocol.Result.Accepted -> parsed.command
        }

        val repository = app.dnsRepository
        val ordered = isOrderedBroadcast // dibaca sebelum goAsync(): setelahnya selalu false
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Batas waktu di bawah tenggat broadcast. Bila habis saat probe/self-healing, DnsApplier
                // membatalkan dengan aman dan memulihkan konfigurasi semula (diuji di DnsApplierTest).
                val ok = withTimeoutOrNull(TIMEOUT_MS) {
                    when (command) {
                        AutomationCommand.Enable -> repository.enableQuick() is ApplyResult.Success
                        AutomationCommand.Disable -> repository.disable()
                        is AutomationCommand.Pause -> repository.pause(command.minutes * 60_000L) is PauseResult.Paused
                        is AutomationCommand.Apply -> repository.apply(command.hostname, probe = true) is ApplyResult.Success
                    }
                } ?: false
                Log.i(TAG, "Perintah otomasi $command: ${if (ok) "berhasil" else "gagal"}")
                if (ordered) pending.resultCode = if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED
            } finally {
                pending.finish()
            }
        }
    }

    /** Tasker/MacroDroid bisa mengirim extra sebagai String maupun angka; semua dinormalisasi ke String. */
    private fun Intent.stringExtras(): Map<String, String?> {
        val bundle = extras ?: return emptyMap()
        return bundle.keySet().associateWith {
            @Suppress("DEPRECATION")
            bundle.get(it)?.toString()
        }
    }

    private companion object {
        const val TAG = "AetherDnsAutomation"
        const val TIMEOUT_MS = 9_000L
    }
}
