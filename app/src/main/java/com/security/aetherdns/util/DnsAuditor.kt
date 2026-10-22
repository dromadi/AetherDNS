package com.security.aetherdns.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

enum class AuditOutcome {
    /** Di-resolve ke 0.0.0.0 / localhost. */
    SINKHOLE,
    /** Domain tidak ada (diblokir di level DNS). */
    NXDOMAIN,
    /** Di-resolve ke IP publik: tidak diblokir. */
    RESOLVED,
    /** Kegagalan lain; tidak dihitung sebagai terblokir. */
    ERROR,
    /**
     * HP masih memberi IP publik, tetapi resolver aktif (ditanya langsung lewat DoT) memblokirnya: jawaban lama
     * dari cache DNS HP sejak sebelum proteksi aktif. Akan terblokir setelah cache kedaluwarsa.
     */
    CACHED;

    val isBlocked: Boolean get() = this == SINKHOLE || this == NXDOMAIN || this == CACHED
}

data class AuditItem(
    val domain: String,
    val label: String,
    val outcome: AuditOutcome,
    /** IP hasil resolusi, atau nama exception untuk [AuditOutcome.ERROR]. */
    val detail: String = ""
) {
    val isBlocked: Boolean get() = outcome.isBlocked
}

data class AuditSummary(
    /** false bila domain kontrol pun gagal di-resolve: koneksi/resolver mati, hasil audit tidak bermakna. */
    val isNetworkReachable: Boolean,
    val isOverallProtected: Boolean,
    val blockedCount: Int,
    val totalCount: Int,
    val items: List<AuditItem>,
    val timestamp: Long = System.currentTimeMillis()
) {
    val cachedCount: Int get() = items.count { it.outcome == AuditOutcome.CACHED }

    /**
     * Mencocokkan hasil DNS sistem dengan jawaban resolver aktif yang ditanya langsung: domain yang masih
     * di-resolve HP tetapi diblokir resolver ditandai [AuditOutcome.CACHED], bukan bocor.
     */
    fun reconcileWith(blockedByResolver: Set<String>): AuditSummary {
        if (!isNetworkReachable || blockedByResolver.isEmpty()) return this
        val reconciled = items.map { item ->
            if (item.outcome == AuditOutcome.RESOLVED && item.domain in blockedByResolver) {
                item.copy(outcome = AuditOutcome.CACHED)
            } else {
                item
            }
        }
        val blocked = reconciled.count { it.isBlocked }
        return copy(items = reconciled, blockedCount = blocked, isOverallProtected = blocked >= DnsAuditor.PROTECTED_THRESHOLD)
    }
}

/** Resolver sistem (melalui Private DNS yang aktif); diabstraksi agar audit dapat diuji di JVM. */
fun interface SystemResolver {
    /** @throws UnknownHostException bila NXDOMAIN. */
    fun resolve(domain: String): List<String>
}

class DnsAuditor(
    private val resolver: SystemResolver = SystemResolver { domain ->
        InetAddress.getAllByName(domain).map { it.hostAddress.orEmpty() }
    }
) {
    suspend fun runAudit(): AuditSummary = withContext(Dispatchers.IO) {
        val testDomains = AdTestDomains.all

        // 1. Verifikasi konektivitas dasar via domain kontrol
        val control = check(CONTROL_DOMAIN, "")
        if (control.outcome != AuditOutcome.RESOLVED) {
            return@withContext AuditSummary(
                isNetworkReachable = false,
                isOverallProtected = false,
                blockedCount = 0,
                totalCount = testDomains.size,
                items = emptyList()
            )
        }

        // 2. Evaluasi domain iklan pihak ketiga
        val items = testDomains.map { (domain, label) -> check(domain, label) }
        val blocked = items.count { it.isBlocked }
        AuditSummary(
            isNetworkReachable = true,
            isOverallProtected = blocked >= PROTECTED_THRESHOLD,
            blockedCount = blocked,
            totalCount = testDomains.size,
            items = items
        )
    }

    private fun check(domain: String, label: String): AuditItem = try {
        val addresses = resolver.resolve(domain)
        val joined = addresses.joinToString(", ")
        if (addresses.any { it in SINKHOLE_ADDRESSES }) {
            AuditItem(domain, label, AuditOutcome.SINKHOLE, joined)
        } else {
            AuditItem(domain, label, AuditOutcome.RESOLVED, joined)
        }
    } catch (e: UnknownHostException) {
        AuditItem(domain, label, AuditOutcome.NXDOMAIN)
    } catch (e: Exception) {
        AuditItem(domain, label, AuditOutcome.ERROR, e.javaClass.simpleName)
    }

    companion object {
        const val CONTROL_DOMAIN = "connectivitycheck.gstatic.com"
        internal const val PROTECTED_THRESHOLD = 2
        private val SINKHOLE_ADDRESSES = setOf("0.0.0.0", "127.0.0.1", "::", "::1")
    }
}
