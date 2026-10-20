package com.security.aetherdns.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.security.aetherdns.R

/**
 * App Shortcuts (tekan lama ikon aplikasi). Didaftarkan secara dinamis agar intent selalu menunjuk
 * ke applicationId yang benar (build debug memakai sufiks `.debug`, yang tidak bisa ditulis di shortcuts.xml).
 */
object AppShortcuts {
    const val ACTION_ENABLE = "com.security.aetherdns.action.SHORTCUT_ENABLE"
    const val ACTION_DISABLE = "com.security.aetherdns.action.SHORTCUT_DISABLE"
    const val ACTION_PAUSE = "com.security.aetherdns.action.SHORTCUT_PAUSE"

    const val PAUSE_MINUTES = 15

    private data class Spec(val id: String, val action: String, val shortLabel: Int, val longLabel: Int, val icon: Int)

    private val specs = listOf(
        Spec("enable", ACTION_ENABLE, R.string.shortcut_enable_short, R.string.shortcut_enable_long, R.drawable.ic_shortcut_enable),
        Spec("pause", ACTION_PAUSE, R.string.shortcut_pause_short, R.string.shortcut_pause_long, R.drawable.ic_shortcut_pause),
        Spec("disable", ACTION_DISABLE, R.string.shortcut_disable_short, R.string.shortcut_disable_long, R.drawable.ic_shortcut_disable)
    )

    /** Dipanggil saat aplikasi dibuka; menimpa daftar lama sehingga label ikut bahasa terbaru. */
    fun publish(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcuts = specs.mapIndexed { rank, spec ->
            ShortcutInfo.Builder(context, spec.id)
                .setShortLabel(context.getString(spec.shortLabel))
                .setLongLabel(context.getString(spec.longLabel))
                .setIcon(Icon.createWithResource(context, spec.icon))
                .setRank(rank)
                .setIntent(Intent(context, ShortcutActivity::class.java).setAction(spec.action))
                .build()
        }
        runCatching { manager.dynamicShortcuts = shortcuts }
    }
}
