package com.security.aetherdns.ui

import androidx.annotation.StringRes
import com.security.aetherdns.R
import com.security.aetherdns.data.DnsState
import com.security.aetherdns.data.LatencySummary
import com.security.aetherdns.data.SpeedLevel
import com.security.aetherdns.util.AuditSummary
import com.security.aetherdns.util.CheckResult
import com.security.aetherdns.util.NetworkCheck
import com.security.aetherdns.util.NetworkReport

enum class HealthLevel { GOOD, INFO, PENDING, WARNING, BAD }

data class HealthRow(val level: HealthLevel, @StringRes val text: Int, val args: List<Any> = emptyList())

data class HealthSummary(val rows: List<HealthRow>) {
    /** Tingkat terburuk menentukan judul kartu. */
    val level: HealthLevel get() = rows.maxOfOrNull { it.level } ?: HealthLevel.PENDING

    /** DNS terenkripsi diblokir di jaringan ini saat proteksi aktif: tawarkan jeda. */
    val suggestPause: Boolean get() = rows.any { it.text == R.string.health_dot_blocked }
}

/**
 * Menerjemahkan hasil teknis (status DNS, cek jaringan, audit) menjadi beberapa baris berbahasa awam untuk Beranda.
 * Logika murni, diuji di `HealthModelTest`; detail teknis tetap tersedia di bagian "Detail" kartu.
 */
object HealthModel {

    /**
     * @param blocksAds true bila resolver aktif adalah preset pemblokir iklan, false bila preset yang memang tidak
     *   memblokir iklan (keamanan / enkripsi saja), null bila tidak diketahui (hostname kustom).
     */
    fun summarize(
        state: DnsState,
        blocksAds: Boolean?,
        report: NetworkReport?,
        audit: AuditSummary?,
        speed: LatencySummary? = null
    ): HealthSummary {
        val rows = mutableListOf<HealthRow>()
        val active = state.isProtectionActive

        // 1. Enkripsi
        rows += when {
            !active -> HealthRow(HealthLevel.BAD, R.string.health_encryption_off)
            state.isSystemDoTValidated -> HealthRow(HealthLevel.GOOD, R.string.health_encryption_on)
            else -> HealthRow(HealthLevel.PENDING, R.string.health_encryption_pending)
        }

        // 2. Pemblokiran iklan (hanya bermakna saat proteksi aktif)
        if (active) {
            when {
                blocksAds == false -> rows += HealthRow(HealthLevel.INFO, R.string.health_ads_not_filtering)
                audit == null -> rows += HealthRow(HealthLevel.PENDING, R.string.health_ads_pending)
                !audit.isNetworkReachable -> Unit
                audit.blockedCount == audit.totalCount ->
                    rows += HealthRow(HealthLevel.GOOD, R.string.health_ads_blocked, listOf(audit.blockedCount, audit.totalCount))
                audit.blockedCount > 0 ->
                    rows += HealthRow(HealthLevel.WARNING, R.string.health_ads_partial, listOf(audit.blockedCount, audit.totalCount))
                else -> rows += HealthRow(HealthLevel.BAD, R.string.health_ads_none)
            }
        }

        // Kecepatan: hanya disebut bila lambat secara konsisten (cukup sampel), bukan dari satu pengukuran
        val attempts = (speed?.samples ?: 0) + (speed?.failures ?: 0)
        // ≥ 20% percobaan gagal (dari minimal 5): penyebab umum internet "putus-nyambung" dalam mode Private DNS
        if (active && speed != null && attempts >= 5 && speed.failures * 5 >= attempts) {
            rows += HealthRow(HealthLevel.WARNING, R.string.health_dns_unreliable)
        } else if (active && speed?.level == SpeedLevel.SLOW) {
            rows += HealthRow(HealthLevel.WARNING, R.string.health_speed_slow, listOf(speed.medianMs))
        }

        // 3. Perilaku ISP & jaringan
        if (report == null) {
            rows += HealthRow(HealthLevel.PENDING, R.string.health_network_pending)
        } else {
            val tamperingKnown = report[NetworkCheck.INTERCEPTION] != CheckResult.INCONCLUSIVE ||
                report[NetworkCheck.NXDOMAIN_HIJACK] != CheckResult.INCONCLUSIVE
            when {
                report.isPlainDnsTampered && active -> rows += HealthRow(HealthLevel.INFO, R.string.health_isp_tampers_protected)
                report.isPlainDnsTampered -> rows += HealthRow(HealthLevel.WARNING, R.string.health_isp_tampers_exposed)
                tamperingKnown -> rows += HealthRow(HealthLevel.GOOD, R.string.health_isp_clean)
            }
            when (report[NetworkCheck.DOT_REACHABLE]) {
                CheckResult.PROBLEM -> rows += HealthRow(
                    if (active) HealthLevel.BAD else HealthLevel.WARNING,
                    if (active) R.string.health_dot_blocked else R.string.health_dot_blocked_inactive
                )
                // Saat proteksi aktif & tervalidasi, baris enkripsi sudah mewakili; tampilkan hanya bila belum aktif
                CheckResult.OK -> if (!active) rows += HealthRow(HealthLevel.GOOD, R.string.health_dot_ok)
                CheckResult.INCONCLUSIVE -> Unit
            }
        }
        return HealthSummary(rows)
    }
}
