package com.security.aetherdns.autopilot

import com.security.aetherdns.data.ProtectionSnapshot
import com.security.aetherdns.util.Reachability

/** Alasan Autopilot mengambil alih; teks untuk pengguna dipetakan di lapisan Android/UI. */
enum class AutopilotReason {
    /** Wi-Fi meminta login (hotel, kafe, bandara). */
    CAPTIVE_PORTAL,

    /** Jaringan memblokir DNS terenkripsi (port 853) untuk semua resolver. */
    DOT_BLOCKED,

    /** Hanya resolver pilihan pengguna yang tidak terjangkau dari jaringan ini. */
    RESOLVER_DOWN
}

/**
 * Jejak tindakan Autopilot yang masih berlaku. [original] = resolver pilihan pengguna; [expected] = status yang
 * ditinggalkan Autopilot. Bila status sistem berbeda dari [expected], pengguna sudah turun tangan dan
 * Autopilot melepaskan kendali.
 */
data class AutopilotHold(
    val original: String,
    val reason: AutopilotReason,
    val expected: ProtectionSnapshot,
    val since: Long,
    /** Resolver pengganti bila Autopilot memindahkan, null bila menjeda. */
    val substitute: String? = null
) {
    val isPause: Boolean get() = substitute == null
}

sealed class AutopilotDecision {
    data object None : AutopilotDecision()
    data class Pause(val reason: AutopilotReason) : AutopilotDecision()
    data class SwitchTo(val resolver: String) : AutopilotDecision()

    /** Kembali ke resolver pilihan pengguna (dari jeda atau dari resolver pengganti). */
    data class Restore(val resolver: String) : AutopilotDecision()
}

/** Fakta yang dikumpulkan sebelum memutuskan. */
data class AutopilotInput(
    val snapshot: ProtectionSnapshot,
    /** Jeda manual pengguna (tombol Jeda 15 menit) sedang berjalan. */
    val userPaused: Boolean,
    val hold: AutopilotHold?,
    val captivePortal: Boolean,
    /**
     * Android sendiri melaporkan DNS terenkripsi tersambung dan jaringan tervalidasi. Bila begitu, probe milik
     * aplikasi yang gagal berarti aplikasi dibatasi (Doze, hemat baterai), bukan DNS-nya: jangan bertindak.
     */
    val systemDnsHealthy: Boolean = false,
    val now: Long = 0L
)

/**
 * Logika murni Autopilot (diuji di `AutopilotPolicyTest`). Prinsip: hanya bertindak saat DNS terenkripsi
 * pasti membuat internet tidak bisa dipakai, tidak pernah melawan pilihan manual pengguna, dan selalu
 * kembali ke resolver pilihan pengguna begitu bisa.
 */
object AutopilotPolicy {

    /** Hold yang masih berlaku; null bila pengguna sudah mengubah sesuatu sejak Autopilot bertindak. */
    fun validHold(hold: AutopilotHold?, snapshot: ProtectionSnapshot): AutopilotHold? =
        hold?.takeIf { it.expected == snapshot }

    /** Resolver pengganti dipertahankan minimal selama ini agar tidak bolak-balik di jaringan yang goyah. */
    const val MIN_SUBSTITUTE_HOLD_MS = 5 * 60_000L

    /**
     * @param plainInternet true bila DNS biasa (port 53) masih menjawab: membedakan "DNS terenkripsi diblokir"
     * dari "aplikasi / jaringan sedang tidak bisa dipakai sama sekali".
     */
    suspend fun decide(
        input: AutopilotInput,
        alternatives: List<String>,
        reference: String,
        plainInternet: suspend () -> Boolean = { true },
        reachable: suspend (String) -> Reachability
    ): AutopilotDecision {
        if (input.userPaused) return AutopilotDecision.None
        val hold = validHold(input.hold, input.snapshot)
        val active = input.snapshot.active
        // Proteksi dimatikan pengguna sendiri: bukan urusan Autopilot
        if (!active && hold == null) return AutopilotDecision.None

        val preferred = hold?.original ?: input.snapshot.specifier
        if (preferred.isBlank()) return AutopilotDecision.None

        if (input.captivePortal) {
            return if (active) AutopilotDecision.Pause(AutopilotReason.CAPTIVE_PORTAL) else AutopilotDecision.None
        }

        // Proteksi aktif dan Android sendiri tersambung: tidak ada yang perlu diperbaiki
        if (active && input.systemDnsHealthy && hold?.substitute == null) return AutopilotDecision.None

        return when (reachable(preferred)) {
            Reachability.OK -> when {
                hold == null -> AutopilotDecision.None
                hold.substitute != null && input.now - hold.since < MIN_SUBSTITUTE_HOLD_MS -> AutopilotDecision.None
                else -> AutopilotDecision.Restore(preferred)
            }
            // Jaringan belum siap / offline: jangan mengubah apa pun berdasarkan data kosong
            Reachability.OFFLINE -> AutopilotDecision.None
            Reachability.FAIL -> if (!plainInternet()) AutopilotDecision.None else when (reachable(reference)) {
                Reachability.OFFLINE -> AutopilotDecision.None
                Reachability.FAIL ->
                    if (active) AutopilotDecision.Pause(AutopilotReason.DOT_BLOCKED) else AutopilotDecision.None
                Reachability.OK -> {
                    // Pengganti yang sedang dipakai masih sehat: biarkan
                    val substitute = hold?.substitute
                    if (substitute != null && active && reachable(substitute) == Reachability.OK) {
                        return AutopilotDecision.None
                    }
                    val alternative = alternatives
                        .filter { !it.equals(preferred, ignoreCase = true) && !it.equals(substitute, ignoreCase = true) }
                        .firstOrNull { reachable(it) == Reachability.OK }
                    when {
                        alternative != null -> AutopilotDecision.SwitchTo(alternative)
                        active -> AutopilotDecision.Pause(AutopilotReason.RESOLVER_DOWN)
                        else -> AutopilotDecision.None
                    }
                }
            }
        }
    }
}
