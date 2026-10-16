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
    val isOverallProtected: Boolean,
    val blockedCount: Int,
    val totalCount: Int,
    val items: List<AuditItem>,
    val timestamp: Long = System.currentTimeMillis()
)

object DnsAuditor {
    private val testDomains = listOf(
        Pair("pagead2.googlesyndication.com", "Google AdMob Delivery"),
        Pair("adservice.google.com", "Google Ad Services"),
        Pair("ads.doubleclick.net", "DoubleClick Advertising"),
        Pair("applovin.com", "AppLovin Ad SDK")
    )

    suspend fun runAudit(): AuditSummary = withContext(Dispatchers.IO) {
        val results = mutableListOf<AuditItem>()
        var blockedCounter = 0

        for ((domain, desc) in testDomains) {
            try {
                val addresses = InetAddress.getAllByName(domain)
                val ipString = addresses.joinToString(", ") { it.hostAddress ?: "" }

                // Check if sinkholed to 0.0.0.0 or localhost
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
                // NXDOMAIN -> Successfully blocked at DNS level
                blockedCounter++
                results.add(AuditItem(domain, desc, true, "NXDOMAIN (Terblokir)"))
            } catch (e: Exception) {
                blockedCounter++
                results.add(AuditItem(domain, desc, true, "Koneksi Ditolak (${e.javaClass.simpleName})"))
            }
        }

        AuditSummary(
            isOverallProtected = blockedCounter >= 2,
            blockedCount = blockedCounter,
            totalCount = testDomains.size,
            items = results
        )
    }
}
