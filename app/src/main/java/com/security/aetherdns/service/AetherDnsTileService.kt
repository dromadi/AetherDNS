package com.security.aetherdns.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.security.aetherdns.MainActivity
import com.security.aetherdns.util.DnsManager

class AetherDnsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        // 1. Jika belum ada izin WRITE_SECURE_SETTINGS, buka MainActivity untuk panduan ADB
        if (!DnsManager.hasSecurePermission(this)) {
            openMainActivity()
            return
        }

        // 2. Proteksi Lockscreen: Pastikan perangkat dibuka sebelum mengubah DNS
        if (isSecure) {
            unlockAndRun {
                performToggle()
            }
        } else {
            performToggle()
        }
    }

    private fun performToggle() {
        val currentMode = DnsManager.getPrivateDnsMode(this)
        val isCurrentlyActive = currentMode == DnsManager.MODE_HOSTNAME

        if (isCurrentlyActive) {
            DnsManager.disablePrivateDns(this)
        } else {
            val targetHostname = DnsManager.getEffectiveHostname(this)
            DnsManager.applyPrivateDns(this, targetHostname)
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return

        if (!DnsManager.hasSecurePermission(this)) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "DNS Anti-Iklan"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Perlu Izin ADB"
            }
            tile.updateTile()
            return
        }

        val mode = DnsManager.getPrivateDnsMode(this)
        val specifier = DnsManager.getPrivateDnsSpecifier(this)

        if (mode == DnsManager.MODE_HOSTNAME && specifier.isNotBlank()) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "DNS Anti-Iklan"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = specifier
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "DNS Anti-Iklan"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Nonaktif"
            }
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
