package com.security.aetherdns.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.security.aetherdns.AetherDnsApplication
import com.security.aetherdns.MainActivity
import com.security.aetherdns.data.ApplyResult
import com.security.aetherdns.data.DnsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class AetherDnsTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val repository: DnsRepository
        get() = (application as AetherDnsApplication).dnsRepository

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()

        // 1. Jika belum ada izin WRITE_SECURE_SETTINGS, buka MainActivity untuk panduan ADB / Shizuku.
        // Catatan AOSP: Tile harus berstatus STATE_INACTIVE (bukan STATE_UNAVAILABLE) agar klik diteruskan ke sini.
        if (!repository.loadCurrentState().hasPermission) {
            openMainActivity()
            return
        }

        // 2. Proteksi Lockscreen: Pastikan perangkat dibuka sebelum mengubah konfigurasi DNS sistem
        if (isSecure) {
            unlockAndRun { performToggle() }
        } else {
            performToggle()
        }
    }

    private fun performToggle() {
        scope.launch {
            // Penulisan setting diselesaikan walau tile dilepas sistem di tengah jalan
            val failed = withContext(NonCancellable) {
                if (repository.loadCurrentState().isProtectionActive) {
                    !repository.disable()
                } else {
                    // Melanjutkan jeda bila ada; jika tidak, specifier tersimpan dengan fallback ke resolver default
                    repository.enableQuick() is ApplyResult.Failure
                }
            }
            if (failed) openMainActivity()
            updateTileState()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val state = repository.loadCurrentState()
        tile.label = "DNS Anti-Iklan"

        val (tileState, subtitle) = when {
            // STATE_INACTIVE agar klik tetap diterima sistem dan diarahkan ke openMainActivity()
            !state.hasPermission -> Tile.STATE_INACTIVE to "Perlu Izin ADB"
            state.isProtectionActive -> Tile.STATE_ACTIVE to state.specifier
            state.pausedUntil != null ->
                Tile.STATE_INACTIVE to "Dijeda s/d ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.pausedUntil))}"
            else -> Tile.STATE_INACTIVE to "Nonaktif"
        }
        tile.state = tileState
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
