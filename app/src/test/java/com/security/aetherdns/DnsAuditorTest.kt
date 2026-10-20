package com.security.aetherdns

import com.security.aetherdns.util.AdTestDomains
import com.security.aetherdns.util.AuditOutcome
import com.security.aetherdns.util.DnsAuditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class DnsAuditorTest {

    /** Resolver palsu: domain di [answers] di-resolve ke IP tersebut, sisanya NXDOMAIN. */
    private fun auditor(answers: Map<String, List<String>>) = DnsAuditor { domain ->
        answers[domain] ?: throw UnknownHostException(domain)
    }

    private val control = DnsAuditor.CONTROL_DOMAIN to listOf("142.250.4.94")

    @Test
    fun fullyFilteredResolverIsProtected() = runTest {
        val summary = auditor(mapOf(control, "applovin.com" to listOf("0.0.0.0"))).runAudit()

        assertTrue(summary.isNetworkReachable)
        assertTrue(summary.isOverallProtected)
        assertEquals(AdTestDomains.all.size, summary.blockedCount)
        assertEquals(AuditOutcome.SINKHOLE, summary.items.single { it.domain == "applovin.com" }.outcome)
    }

    @Test
    fun unfilteredResolverIsLeaking() = runTest {
        val everything = AdTestDomains.domains.associateWith { listOf("203.0.113.7") } + control
        val summary = auditor(everything).runAudit()

        assertFalse(summary.isOverallProtected)
        assertEquals(0, summary.blockedCount)
        assertTrue(summary.items.all { it.outcome == AuditOutcome.RESOLVED })
    }

    @Test
    fun deadDnsIsReportedAsUnreachableNotProtected() = runTest {
        // Semua resolusi gagal (mis. offline atau resolver strict mati): domain iklan "NXDOMAIN" tidak boleh dianggap terlindungi
        val summary = auditor(emptyMap()).runAudit()

        assertFalse(summary.isNetworkReachable)
        assertFalse(summary.isOverallProtected)
        assertTrue(summary.items.isEmpty())
    }

    @Test
    fun sinkholedControlDomainCountsAsUnreachable() = runTest {
        val summary = auditor(mapOf(DnsAuditor.CONTROL_DOMAIN to listOf("0.0.0.0"))).runAudit()
        assertFalse(summary.isNetworkReachable)
    }

    @Test
    fun resolverErrorsAreNotCountedAsBlocked() = runTest {
        val summary = DnsAuditor { domain ->
            if (domain == DnsAuditor.CONTROL_DOMAIN) listOf("142.250.4.94") else throw SecurityException("denied")
        }.runAudit()

        assertEquals(0, summary.blockedCount)
        assertTrue(summary.items.all { it.outcome == AuditOutcome.ERROR && it.detail == "SecurityException" })
    }
}
