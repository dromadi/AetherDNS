package com.security.aetherdns.autopilot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.security.aetherdns.MainActivity
import com.security.aetherdns.R
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.ui.displayName

/** Satu notifikasi senyap yang menjelaskan tindakan Autopilot terakhir (diganti setiap tindakan baru). */
class AutopilotNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun paused(reason: AutopilotReason, resolver: String) = post(
        context.getString(R.string.autopilot_notif_paused_title),
        when (reason) {
            AutopilotReason.CAPTIVE_PORTAL -> context.getString(R.string.autopilot_reason_captive)
            AutopilotReason.DOT_BLOCKED -> context.getString(R.string.autopilot_reason_blocked)
            AutopilotReason.RESOLVER_DOWN -> context.getString(R.string.autopilot_reason_down, name(resolver))
        } + " " + context.getString(R.string.autopilot_notif_paused_tail)
    )

    fun switched(from: String, to: String) = post(
        context.getString(R.string.autopilot_notif_switched_title),
        context.getString(R.string.autopilot_notif_switched_body, name(from), name(to))
    )

    fun restored(resolver: String) = post(
        context.getString(R.string.autopilot_notif_restored_title),
        context.getString(R.string.autopilot_notif_restored_body, name(resolver))
    )

    fun cancel() {
        manager?.cancel(NOTIFICATION_ID)
    }

    private fun name(hostname: String): String =
        DefaultProfiles.list.firstOrNull { it.hostname.equals(hostname, ignoreCase = true) }?.displayName(context) ?: hostname

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(title: String, text: String) {
        if (!canPost() || manager == null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.autopilot_notif_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Izin notifikasi dicabut
        }
    }

    private companion object {
        const val CHANNEL_ID = "autopilot"
        const val NOTIFICATION_ID = 2001
    }
}
