package com.security.aetherdns.adb

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.security.aetherdns.MainActivity
import com.security.aetherdns.R
import com.security.aetherdns.ui.toUiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Notifikasi input kode penyambungan. Membalas notifikasi tidak menutup layar Settings, sehingga dialog
 * "kode penyambungan" tetap terbuka (pada banyak ROM, termasuk One UI, dialog itu batal bila Settings ditinggalkan).
 */
class PairingNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** true sejak pengguna meminta input lewat notifikasi sampai alur selesai. */
    private var active = false

    /** Menampilkan notifikasi mengikuti [WirelessAdbActivator.state] (hanya bila alur notifikasi dipakai). */
    fun bind(scope: CoroutineScope, activator: WirelessAdbActivator) {
        scope.launch {
            activator.state.collect { state ->
                when (state) {
                    ActivationState.AwaitingCode -> {
                        active = true
                        post(inputNotification(context.getString(R.string.native_notif_input_text)))
                    }
                    is ActivationState.Working -> if (active) post(progressNotification(state.step))
                    ActivationState.Success -> if (active) {
                        active = false
                        post(resultNotification(context.getString(R.string.native_success)))
                    }
                    is ActivationState.Failed -> if (active) {
                        post(inputNotification(state.error.toUiText().resolve(context), isRetry = true))
                    }
                    ActivationState.Idle -> {
                        active = false
                        manager.cancel(NOTIFICATION_ID)
                    }
                }
            }
        }
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(notification: android.app.Notification) {
        if (!canPost()) return
        ensureChannel()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Izin notifikasi dicabut di tengah alur
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.native_notif_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_shield)
            .setContentTitle(context.getString(R.string.native_notif_title))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())

    private fun inputNotification(text: String, isRetry: Boolean = false): android.app.Notification {
        val remoteInput = RemoteInput.Builder(KEY_CODE)
            .setLabel(context.getString(R.string.native_notif_input_label))
            .build()
        // RemoteInput wajib PendingIntent mutable agar sistem bisa menyisipkan teks balasan
        val replyIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_REPLY,
            Intent(context, PairingCodeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_tile_shield,
            context.getString(if (isRetry) R.string.native_notif_action_retry else R.string.native_notif_action),
            replyIntent
        ).addRemoteInput(remoteInput).setAllowGeneratedReplies(false).build()

        return baseBuilder()
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .addAction(action)
            .setOnlyAlertOnce(!isRetry)
            .build()
    }

    private fun progressNotification(step: ActivationStep): android.app.Notification {
        val text = step.toUiText().resolve(context)
        return baseBuilder()
            .setContentText(text)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
    }

    private fun resultNotification(text: String): android.app.Notification =
        baseBuilder()
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setTimeoutAfter(RESULT_TIMEOUT_MS)
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val KEY_CODE = "pairing_code"
        private const val CHANNEL_ID = "wireless_activation"
        private const val NOTIFICATION_ID = 853
        private const val REQUEST_REPLY = 1
        private const val REQUEST_OPEN = 2
        private const val RESULT_TIMEOUT_MS = 15_000L
    }
}
