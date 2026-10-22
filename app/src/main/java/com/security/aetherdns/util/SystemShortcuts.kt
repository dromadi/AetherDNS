package com.security.aetherdns.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Pintasan ke layar sistem / aplikasi yang dibutuhkan saat menyiapkan Shizuku. */
object SystemShortcuts {
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"

    /** Kunci preferensi "Wireless debugging" di Developer options AOSP; dipakai untuk menyorot item itu. */
    private const val WIRELESS_DEBUGGING_KEY = "toggle_adb_wireless"

    enum class DeveloperTarget { WIRELESS_DEBUGGING, ABOUT_PHONE }

    fun isDeveloperOptionsEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    /**
     * Membuka Developer options dengan item Wireless debugging disorot (diabaikan oleh ROM yang tidak mendukung).
     * Bila Developer options belum aktif, membuka About phone agar pengguna bisa mengetuk Build number.
     */
    fun openWirelessDebugging(context: Context): DeveloperTarget {
        if (isDeveloperOptionsEnabled(context)) {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .putExtra(":settings:fragment_args_key", WIRELESS_DEBUGGING_KEY)
            if (start(context, intent)) return DeveloperTarget.WIRELESS_DEBUGGING
        }
        start(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) || start(context, Intent(Settings.ACTION_SETTINGS))
        return DeveloperTarget.ABOUT_PHONE
    }

    /** Membuka aplikasi Shizuku, atau halaman unduhannya bila belum terpasang. */
    fun openShizuku(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launch == null || !start(context, launch)) {
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL)))
        }
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
