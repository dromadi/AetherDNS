package com.security.aetherdns

import com.security.aetherdns.data.DnsState
import com.security.aetherdns.ui.HealthLevel
import com.security.aetherdns.ui.HealthModel
import com.security.aetherdns.util.AuditSummary
import com.security.aetherdns.util.CheckResult
import com.security.aetherdns.util.NetworkCheck
import com.security.aetherdns.util.NetworkReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthModelTest {

    private val active = DnsState(
        hasPermission = true, mode = "hostname", specifier = "dns.adguard-dns.com",
        isProtectionActive = true, isSystemDoTValidated = true
    )

    private fun report(interception: CheckResult = CheckResult.OK, hijack: CheckResult = CheckResult.OK, dot: CheckResult = CheckResult.OK) =
        NetworkReport(mapOf(NetworkCheck.INTERCEPTION to interception, NetworkCheck.NXDOMAIN_HIJACK to hijack, NetworkCheck.DOT_REACHABLE to dot))

    private fun audit(blocked: Int, total: Int = 4) =
        AuditSummary(isNetworkReachable = true, isOverallProtected = blocked >= 2, blockedCount = blocked, totalCount = total, items = emptyList())

    private fun texts(summary: com.security.aetherdns.ui.HealthSummary) = summary.rows.map { it.text }

    @Test
    fun `all good when encrypted, ads blocked and network clean`() {
        val summary = HealthModel.summarize(active, blocksAds = true, report = report(), audit = audit(4))
        assertEquals(HealthLevel.GOOD, summary.level)
        assertEquals(listOf(R.string.health_encryption_on, R.string.health_ads_blocked, R.string.health_isp_clean), texts(summary))
        assertFalse(summary.suggestPause)
    }

    @Test
    fun `isp tampering while protected is information, not a warning`() {
        val summary = HealthModel.summarize(active, true, report(interception = CheckResult.PROBLEM), audit(4))
        assertTrue(R.string.health_isp_tampers_protected in texts(summary))
        assertEquals(HealthLevel.INFO, summary.level)
    }

    @Test
    fun `protection off is the worst state`() {
        val off = active.copy(isProtectionActive = false, isSystemDoTValidated = false)
        val summary = HealthModel.summarize(off, true, report(hijack = CheckResult.PROBLEM), null)
        assertEquals(HealthLevel.BAD, summary.level)
        assertTrue(R.string.health_isp_tampers_exposed in texts(summary))
        assertTrue(R.string.health_dot_ok in texts(summary))
        // Pemblokiran iklan tidak relevan saat proteksi mati
        assertFalse(texts(summary).any { it == R.string.health_ads_pending || it == R.string.health_ads_blocked })
    }

    @Test
    fun `blocked encrypted dns while active suggests pausing`() {
        val summary = HealthModel.summarize(active.copy(isSystemDoTValidated = false), true, report(dot = CheckResult.PROBLEM), audit(0))
        assertTrue(summary.suggestPause)
        assertEquals(HealthLevel.BAD, summary.level)
    }

    @Test
    fun `non filtering resolver says so instead of reporting leaks`() {
        val summary = HealthModel.summarize(active, blocksAds = false, report = report(), audit = audit(0))
        assertTrue(R.string.health_ads_not_filtering in texts(summary))
        assertFalse(R.string.health_ads_none in texts(summary))
    }

    @Test
    fun `partial blocking is a warning and pending checks are shown`() {
        assertEquals(HealthLevel.WARNING, HealthModel.summarize(active, null, report(), audit(2)).level)
        val pending = HealthModel.summarize(active.copy(isSystemDoTValidated = false), true, null, null)
        assertEquals(
            listOf(R.string.health_encryption_pending, R.string.health_ads_pending, R.string.health_network_pending),
            texts(pending)
        )
    }

    @Test
    fun `consistently slow resolver adds a warning, fast one stays quiet`() {
        val slow = com.security.aetherdns.data.LatencySummary("dns.adguard-dns.com", com.security.aetherdns.data.NetworkKey("wifi", ""), 980, 5, 0, emptyList())
        val summary = HealthModel.summarize(active, true, report(), audit(4), slow)
        assertTrue(R.string.health_speed_slow in texts(summary))
        assertEquals(HealthLevel.WARNING, summary.level)
        val fast = slow.copy(medianMs = 40)
        assertFalse(R.string.health_speed_slow in texts(HealthModel.summarize(active, true, report(), audit(4), fast)))
    }

    @Test
    fun `frequent failures warn about unreliable DNS instead of speed`() {
        val flaky = com.security.aetherdns.data.LatencySummary("dns.adguard-dns.com", com.security.aetherdns.data.NetworkKey("wifi", ""), 980, 4, 2, emptyList())
        val rows = texts(HealthModel.summarize(active, true, report(), audit(4), flaky))
        assertTrue(R.string.health_dns_unreliable in rows)
        assertFalse(R.string.health_speed_slow in rows)
        val rare = flaky.copy(samples = 10, failures = 1)
        assertFalse(R.string.health_dns_unreliable in texts(HealthModel.summarize(active, true, report(), audit(4), rare)))
    }
}
