package com.security.aetherdns.data

import com.security.aetherdns.util.DnsManager

/** Penyimpanan waktu akhir jeda (epoch ms); bertahan setelah aplikasi ditutup. */
interface PauseStore {
    fun pausedUntil(): Long?
    fun save(untilMs: Long)
    fun clear()
}

/** Satu alarm sekali-jalan untuk mengaktifkan kembali proteksi; tidak ada service yang berjalan terus. */
interface ResumeAlarm {
    fun schedule(atMs: Long)
    fun cancel()
}

enum class PauseError { NO_PERMISSION, NOT_ACTIVE, WRITE_REJECTED }

sealed class PauseResult {
    data class Paused(val untilMs: Long) : PauseResult()
    data class Failure(val error: PauseError) : PauseResult()
}

/**
 * Jeda proteksi sementara (mis. untuk login Wi-Fi captive portal):
 * mode Private DNS dimatikan, specifier dibiarkan, lalu alarm mengaktifkannya kembali.
 */
class PauseController(
    private val settings: SecureSettings,
    private val applier: DnsApplier,
    private val store: PauseStore,
    private val alarm: ResumeAlarm,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun pausedUntil(): Long? = store.pausedUntil()

    fun pause(durationMs: Long): PauseResult {
        if (!settings.hasPermission()) {
            return PauseResult.Failure(PauseError.NO_PERMISSION)
        }
        if (settings.mode() != DnsManager.MODE_HOSTNAME || settings.specifier().isBlank()) {
            return PauseResult.Failure(PauseError.NOT_ACTIVE)
        }

        val until = clock() + durationMs
        // Simpan dan jadwalkan lebih dulu: bila proses mati tepat setelah DNS dimatikan, proteksi tetap kembali
        store.save(until)
        alarm.schedule(until)
        val disabled = runCatching { settings.writeMode(DnsManager.MODE_OFF) }.getOrDefault(false)
        if (!disabled) {
            cancel()
            return PauseResult.Failure(PauseError.WRITE_REJECTED)
        }
        settings.onConfigChanged()
        return PauseResult.Paused(until)
    }

    /** Membatalkan jeda tanpa mengubah konfigurasi DNS (dipakai saat pengguna mengubah DNS secara manual). */
    fun cancel() {
        store.clear()
        alarm.cancel()
    }

    /** Mengaktifkan kembali proteksi sekarang juga dengan specifier yang tersimpan di sistem. */
    suspend fun resumeNow(): ApplyResult {
        cancel()
        return reapply()
    }

    /**
     * Dipanggil oleh alarm dan setelah boot. Alarm sistem hilang saat reboot, sehingga jeda yang
     * belum jatuh tempo dijadwalkan ulang dan yang sudah lewat langsung diaktifkan kembali.
     */
    suspend fun onAlarmOrBoot(): ApplyResult? {
        val until = store.pausedUntil() ?: return null
        if (clock() < until) {
            alarm.schedule(until)
            return null
        }
        store.clear()
        // Pengguna sudah mengaktifkan DNS lain secara manual selama jeda: hormati pilihannya
        if (settings.mode() != DnsManager.MODE_OFF) return null
        return reapply()
    }

    private suspend fun reapply(): ApplyResult {
        val hostname = settings.specifier().ifBlank { DnsManager.DEFAULT_FALLBACK_HOSTNAME }
        // Tanpa probe: resolver ini aktif sebelum jeda, dan proteksi harus kembali walau jaringan belum siap
        return applier.apply(hostname, probe = false)
    }
}
