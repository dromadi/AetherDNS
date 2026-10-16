package com.security.aetherdns.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

data class AuditItem(
    val domain: String,
    val description: String,
    val isBlocked: Boolean,
    val resolvedIp: String
)

data class AuditSummary(
    val isNetworkReachable: Boolean,
    val isOverallProtected: Boolean,
    val blockedCount: Int,
    val totalCount: Int,
    val statusMessage: String,
    val items: List<AuditItem>,
    val timestamp: Long = System.currentTimeMillis()
)

object DnsAuditor {
    private const val CONTROL_DOMAIN = "connectivitycheck.gstatic.com"
    private const val CONTROL_DESC = "Domain Kontrol (Konektivitas Internet)"

    private val testDomains = listOf(
        Pair("pagead2.googlesyndication.com", "Google AdMob Delivery"),
        Pair("adservice.google.com", "Google Ad Services"),
        Pair("ads.doubleclick.net", "DoubleClick Advertising"),
        Pair("applovin.com", "AppLovin Ad SDK")
    )

    suspend fun runAudit(): AuditSummary = withContext(Dispatchers.IO) {
        val results = mutableListOf<AuditItem>()

        // 1. Verifikasi konektivitas dasar via Control Domain
        var isReachable = false
        var controlIp = ""
        try {
            val controlAddresses = InetAddress.getAllByName(CONTROL_DOMAIN)
            if (controlAddresses.isNotEmpty()) {
                val firstIp = controlAddresses[0].hostAddress ?: ""
                // Pastikan bukan sinkhole
                if (firstIp != "0.0.0.0" && firstIp != "127.0.0.1") {
                    isReachable = true
                    controlIp = controlAddresses.joinToString(", ") { it.hostAddress ?: "" }
                }
            }
        } catch (_: Exception) {
            isReachable = false
        }

        // Jika domain kontrol gagal, DNS atau koneksi internet mati/tidak valid
        if (!isReachable) {
            return@withContext AuditSummary(
                isNetworkReachable = false,
                isOverallProtected = false,
                blockedCount = 0,
                totalCount = testDomains.size,
                statusMessage = "Koneksi offline atau resolver DNS DoT gagal dihubungi (Semua resolusi gagal).",
                items = listOf(
                    AuditItem(CONTROL_DOMAIN, CONTROL_DESC, false, "Gagal Menghubungi Server")
                )
            )
        }

        // 2. Evaluasi domain iklan pihak ketiga
        var blockedCounter = 0
        for ((domain, desc) in testDomains) {
            try {
                val addresses = InetAddress.getAllByName(domain)
                val ipString = addresses.joinToString(", ") { it.hostAddress ?: "" }

                // Cek apakah di-sinkhole ke 0.0.0.0 atau localhost
                val isSinkhole = addresses.any {
                    val addr = it.hostAddress ?: ""
                    addr == "0.0.0.0" || addr == "127.0.0.1" || addr == "::" || addr == "::1"
                }

                if (isSinkhole) {
                    blockedCounter++
                    results.add(AuditItem(domain, desc, true, "Sinkhole ($ipString)"))
                } else {
                    results.add(AuditItem(domain, desc, false, ipString))
                }
            } catch (e: UnknownHostException) {
                // NXDOMAIN -> Berhasil diblokir pada level DNS DoT
                blockedCounter++
                results.add(AuditItem(domain, desc, true, "NXDOMAIN (Terfilter)"))
            } catch (e: Exception) {
                results.add(AuditItem(domain, desc, false, "Koneksi Error (${e.javaClass.simpleName})"))
            }
        }

        val isProtected = blockedCounter >= 2
        val message = if (isProtected) {
            "$blockedCounter dari ${testDomains.size} domain telemetri & iklan berhasil disinkhole (NXDOMAIN / 0.0.0.0)."
        } else {
            "Hanya $blockedCounter dari ${testDomains.size} domain iklan terfilter. Domain iklan lainnya masih tembus ke IP publik."
        }

        AuditSummary(
            isNetworkReachable = true,
            isOverallProtected = isProtected,
            blockedCount = blockedCounter,
            totalCount = testDomains.size,
            statusMessage = message,
            items = results
        )
    }
}
