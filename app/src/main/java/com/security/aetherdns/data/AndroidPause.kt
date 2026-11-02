package com.security.aetherdns.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.security.aetherdns.receiver.PauseResumeReceiver

class SharedPrefsPauseStore(context: Context) : PauseStore {
    private val prefs = context.getSharedPreferences("pause_state", Context.MODE_PRIVATE)

    override fun pausedUntil(): Long? = prefs.getLong(KEY_UNTIL, 0L).takeIf { it > 0L }

    // commit() sinkron: nilai harus sudah tersimpan sebelum DNS dimatikan
    override fun save(untilMs: Long) {
        prefs.edit().putLong(KEY_UNTIL, untilMs).commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY_UNTIL).commit()
    }

    private companion object {
        const val KEY_UNTIL = "paused_until"
    }
}

/**
 * Alarm inexact `setAndAllowWhileIdle`: tidak butuh izin SCHEDULE_EXACT_ALARM dan tetap berjalan saat Doze.
 * Konsekuensinya, sistem dapat menunda pengaktifan kembali beberapa menit.
 */
class AlarmManagerResumeAlarm(private val context: Context) : ResumeAlarm {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(atMs: Long) {
        alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pendingIntent())
    }

    override fun cancel() {
        alarmManager?.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, PauseResumeReceiver::class.java).setAction(PauseResumeReceiver.ACTION_RESUME),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
