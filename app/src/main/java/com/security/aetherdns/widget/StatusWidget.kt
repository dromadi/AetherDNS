package com.security.aetherdns.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.security.aetherdns.AetherDnsApplication
import com.security.aetherdns.MainActivity
import com.security.aetherdns.R
import com.security.aetherdns.data.DnsState
import com.security.aetherdns.shortcut.AppShortcuts
import com.security.aetherdns.shortcut.ShortcutActivity
import java.text.DateFormat
import java.util.Date

/**
 * Widget layar utama: status proteksi + tombol aktif/nonaktif dan jeda 15 menit.
 *
 * Tombol memakai [ShortcutActivity] (tidak diekspor) lewat PendingIntent milik aplikasi ini, sehingga tidak ada
 * receiver baru yang bisa dipicu aplikasi lain untuk mematikan proteksi. Tanpa service: tampilan diperbarui oleh
 * [DnsRepository][com.security.aetherdns.data.DnsRepository] setiap state berubah, plus pembaruan periodik sistem.
 */
class StatusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = (context.applicationContext as AetherDnsApplication).dnsRepository.loadCurrentState()
        val views = render(context, state)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        /** Memperbarui semua instance widget; murah dan aman dipanggil walau tidak ada widget terpasang. */
        fun updateAll(context: Context, state: DnsState) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(context, StatusWidget::class.java))
            }.getOrNull() ?: return
            if (ids.isEmpty()) return
            val views = render(context, state)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        /** Untuk screenshot test; sama persis dengan yang dipasang ke launcher. */
        @androidx.annotation.VisibleForTesting
        internal fun renderForPreview(context: Context, state: DnsState): RemoteViews = render(context, state)

        private fun render(context: Context, state: DnsState): RemoteViews {
            val active = state.isProtectionActive
            val paused = !active && state.pausedUntil != null
            val views = RemoteViews(context.packageName, R.layout.widget_status)

            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (active) R.drawable.widget_bg_active else R.drawable.widget_bg_inactive
            )
            views.setTextViewText(
                R.id.widget_title,
                context.getString(
                    when {
                        active -> R.string.widget_title_on
                        paused -> R.string.widget_title_paused
                        else -> R.string.widget_title_off
                    }
                )
            )
            views.setTextViewText(
                R.id.widget_subtitle,
                when {
                    !state.hasPermission -> context.getString(R.string.tile_subtitle_needs_permission)
                    active -> state.specifier
                    paused -> context.getString(
                        R.string.widget_subtitle_paused,
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.pausedUntil!!))
                    )
                    else -> context.getString(R.string.tile_subtitle_off)
                }
            )
            views.setFloat(R.id.widget_icon, "setAlpha", if (active) 1f else 0.6f)

            // Tombol utama: matikan saat aktif; aktifkan / lanjutkan saat mati atau dijeda
            val toggleAction = if (active) AppShortcuts.ACTION_DISABLE else AppShortcuts.ACTION_ENABLE
            views.setImageViewResource(
                R.id.widget_toggle,
                if (active) R.drawable.ic_shortcut_disable else R.drawable.ic_shortcut_enable
            )
            views.setContentDescription(
                R.id.widget_toggle,
                context.getString(if (active) R.string.shortcut_disable_long else R.string.shortcut_enable_long)
            )
            views.setInt(R.id.widget_toggle, "setColorFilter", Color.WHITE)
            views.setOnClickPendingIntent(R.id.widget_toggle, actionIntent(context, toggleAction))

            views.setViewVisibility(R.id.widget_pause, if (active && state.hasPermission) View.VISIBLE else View.GONE)
            views.setInt(R.id.widget_pause, "setColorFilter", Color.WHITE)
            views.setOnClickPendingIntent(R.id.widget_pause, actionIntent(context, AppShortcuts.ACTION_PAUSE))

            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return views
        }

        private fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getActivity(
            context,
            action.hashCode(),
            Intent(context, ShortcutActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private const val REQUEST_OPEN = 100
    }
}
