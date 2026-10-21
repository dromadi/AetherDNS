package com.security.aetherdns

import com.security.aetherdns.diagnostics.CrashInfo
import com.security.aetherdns.diagnostics.DeviceInfo
import com.security.aetherdns.diagnostics.DiagnosticReportFormatter
import com.security.aetherdns.diagnostics.DiagnosticSnapshot
import com.security.aetherdns.diagnostics.FeatureStatus
import com.security.aetherdns.diagnostics.NetworkInfo
import com.security.aetherdns.util.AuditItem
import com.security.aetherdns.util.AuditOutcome
import com.security.aetherdns.util.AuditSummary
import com.security.aetherdns.util.CheckResult
import com.security.aetherdns.util.DotProbeCause
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.NetworkCheck
import com.security.aetherdns.util.NetworkReport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {

    private fun snapshot(
        probe: DotProbeResult? = DotProbeResult.Success(42, 18),
        crash: CrashInfo? = null,
        specifier: String = "abc123.dns.nextdns.io"
    ) = DiagnosticSnapshot(
        generatedAt = 1_760_000_000_000,
        device = DeviceInfo("1.14.1", 18, "debug", "com.security.aetherdns.debug", "samsung", "SM-S928B", "15", 35, "id-ID"),
        network = NetworkInfo("wifi", vpnActive = false, carrier = "Telkomsel", osPrivateDnsActive = true, osPrivateDnsServer = specifier),
        features = FeatureStatus(
            hasPermission = true, mode = "hostname", specifier = specifier, presetName = null,
            protectionActive = true, pausedUntil = null, nativeActivationSupported = true,
            activationState = "Success", shizukuStatus = "NOT_INSTALLED", notificationsAllowed = true,
            automationEnabled = true, nextDnsApiKeySet = true, nextDnsProfileDetected = true,
            savedProfiles = 2, widgetsPlaced = 1, onboardingCompleted = true
        ),
        activeResolverProbe = probe,
        networkReport = NetworkReport(
            mapOf(
                NetworkCheck.INTERCEPTION to CheckResult.PROBLEM,
                NetworkCheck.NXDOMAIN_HIJACK to CheckResult.OK,
                NetworkCheck.DOT_REACHABLE to CheckResult.OK
            )
        ),
        audit = AuditSummary(
            isNetworkReachable = true, isOverallProtected = true, blockedCount = 3, totalCount = 4,
            items = listOf(
                AuditItem("ads.doubleclick.net", "DoubleClick", AuditOutcome.NXDOMAIN),
                AuditItem("applovin.com", "AppLovin", AuditOutcome.CACHED, "34.54.99.24")
            )
        ),
        lastCrash = crash
    )

    @Test
    fun `report covers every section`() {
        val text = DiagnosticReportFormatter.format(snapshot())
        listOf(
            "## App & device", "## Network", "## Protection", "## Activation", "## Network check",
            "## Ad audit (system DNS)", "## Other features", "## Last crash"
        ).forEach { assertTrue("missing $it", it in text) }
        assertTrue("SM-S928B" in text)
        assertTrue("INTERCEPTION: PROBLEM" in text)
        assertTrue("Plain DNS tampered: yes" in text)
        assertTrue("applovin.com: CACHED [34.54.99.24]" in text)
        assertTrue("OK (handshake 42 ms, query 18 ms)" in text)
        assertTrue("none recorded" in text)
    }

    @Test
    fun `secrets are reported only as yes or no`() {
        val text = DiagnosticReportFormatter.format(snapshot())
        // FeatureStatus tidak punya field token / API key; laporan hanya menyebut statusnya
        assertTrue("api key set" in text)
        assertTrue("Automation (Tasker): enabled" in text)
        assertFalse("token" in text.lowercase().replace("tokens", ""))
    }

    @Test
    fun `probe failure and crash are included`() {
        val crash = CrashInfo(1_760_000_100_000, "main", "java.lang.IllegalArgumentException: boom\n\tat a.b.C.d(C.kt:1)")
        val text = DiagnosticReportFormatter.format(snapshot(DotProbeResult.Failure(DotProbeCause.TIMEOUT), crash))
        assertTrue("FAILED TIMEOUT" in text)
        assertTrue("IllegalArgumentException: boom" in text)
        assertTrue("Thread: main" in text)
    }

    @Test
    fun `long stack traces are truncated`() {
        val trace = (1..100).joinToString("\n") { "\tat frame$it" }
        val text = DiagnosticReportFormatter.format(snapshot(crash = CrashInfo(0, "main", trace)))
        assertTrue("frame25" in text)
        assertFalse("frame26\n" in text)
    }
}
