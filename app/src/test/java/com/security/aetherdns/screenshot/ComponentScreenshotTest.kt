package com.security.aetherdns.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.security.aetherdns.data.InsightAnalyzer
import com.security.aetherdns.data.InsightSample
import com.security.aetherdns.data.NetworkKey
import com.security.aetherdns.data.NextDnsSummary
import com.security.aetherdns.ui.InsightContent
import com.security.aetherdns.ui.AuditDetailRows
import com.security.aetherdns.ui.HealthCard
import com.security.aetherdns.ui.HealthModel
import com.security.aetherdns.data.DnsState
import com.security.aetherdns.ui.BlockStatsCard
import com.security.aetherdns.ui.DiagnosticsCard
import com.security.aetherdns.ui.ReportUiState
import com.security.aetherdns.util.AuditItem
import com.security.aetherdns.util.AuditOutcome
import com.security.aetherdns.util.AuditSummary
import com.security.aetherdns.ui.CustomHostnameCard
import com.security.aetherdns.ui.DotBlockedDialog
import com.security.aetherdns.ui.StatsUiState
import com.security.aetherdns.ui.theme.AetherDnsTheme
import com.security.aetherdns.util.CheckResult
import com.security.aetherdns.util.NetworkCheck
import com.security.aetherdns.util.NetworkReport
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** Screenshot komponen dengan state yang sulit dicapai lewat aplikasi utuh (opsional, `AETHERDNS_SCREENSHOTS=1`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ComponentScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        assumeTrue(System.getenv("AETHERDNS_SCREENSHOTS") == "1")
        System.setProperty("roborazzi.test.record", "true")
    }

    @Test
    fun healthStates() {
        val active = DnsState(hasPermission = true, mode = "hostname", specifier = "dns.adguard-dns.com", isProtectionActive = true, isSystemDoTValidated = true)
        val tampered = NetworkReport(
            mapOf(
                NetworkCheck.INTERCEPTION to CheckResult.PROBLEM,
                NetworkCheck.NXDOMAIN_HIJACK to CheckResult.OK,
                NetworkCheck.DOT_REACHABLE to CheckResult.OK
            )
        )
        val blocked = NetworkReport(
            mapOf(
                NetworkCheck.INTERCEPTION to CheckResult.OK,
                NetworkCheck.NXDOMAIN_HIJACK to CheckResult.OK,
                NetworkCheck.DOT_REACHABLE to CheckResult.PROBLEM
            )
        )
        val audit = AuditSummary(true, true, 4, 4, emptyList())
        compose.setContent {
            AetherDnsTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        HealthCard(HealthModel.summarize(active, true, tampered, audit), false, tampered, audit, {}, {}, {})
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                        HealthCard(
                            HealthModel.summarize(active.copy(isSystemDoTValidated = false), true, blocked, null),
                            false, blocked, null, {}, {}, {}
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/9_health.png")
    }

    @Test
    fun blockStatsStates() {
        val summary = NextDnsSummary(
            totalQueries = 18_342,
            blockedQueries = 2_917,
            topBlocked = listOf("app-measurement.com" to 812L, "googleads.g.doubleclick.net" to 404L, "graph.facebook.com" to 233L)
        )
        compose.setContent {
            AetherDnsTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        BlockStatsCard(hasApiKey = true, state = StatsUiState.Loaded(summary), onSaveKey = {}, onRemoveKey = {}, onRefresh = {})
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                        BlockStatsCard(hasApiKey = false, state = StatsUiState.Idle, onSaveKey = {}, onRemoveKey = {}, onRefresh = {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/10_block_stats.png")
    }

    @Test
    fun dotBlockedDialog() {
        compose.setContent {
            AetherDnsTheme(dynamicColor = false) {
                DotBlockedDialog(onPause = {}, onDismiss = {})
            }
        }
        compose.onNode(androidx.compose.ui.test.isDialog()).captureRoboImage("build/screenshots/11_dot_blocked_dialog.png")
    }

    @Test
    fun customHostnameSelected() {
        compose.setContent {
            AetherDnsTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        CustomHostnameCard(
                            isSelected = true,
                            hostname = "abc123.dns.nextdns.io",
                            onHostnameChange = {},
                            onSelect = {},
                            canApply = true,
                            isProbing = false,
                            onApply = {},
                            onSaveProfile = {}
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/12_custom_hostname.png")
    }

    @Test
    fun auditWithLongAddresses() {
        val summary = AuditSummary(
            isNetworkReachable = true,
            isOverallProtected = true,
            blockedCount = 4,
            totalCount = 4,
            items = listOf(
                AuditItem("pagead2.googlesyndication.com", "Google AdMob", AuditOutcome.CACHED, "172.253.158.157, 172.253.158.155, 172.253.158.156, 172.253.158.154"),
                AuditItem("adservice.google.com", "Google Ad Services", AuditOutcome.SINKHOLE, "0.0.0.0"),
                AuditItem("ads.doubleclick.net", "DoubleClick", AuditOutcome.NXDOMAIN),
                AuditItem("applovin.com", "AppLovin", AuditOutcome.RESOLVED, "34.54.99.24, 34.54.99.25, 34.54.99.26")
            )
        )
        compose.setContent {
            AetherDnsTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        Column { AuditDetailRows(summary) }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/13_audit.png")
    }

    @Test
    fun diagnosticsReady() {
        val text = """
            === AetherDNS diagnostic report ===
            Generated: 2026-10-21 14:10:00 UTC

            ## App & device
            App: 1.15.0 (19, debug) com.security.aetherdns.debug
            Device: samsung SM-S928B
            Android: 15 (SDK 35)

            ## Network
            Transport: wifi
            Carrier: Telkomsel
            OS Private DNS active: yes
        """.trimIndent()
        compose.setContent {
            AetherDnsTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        DiagnosticsCard(state = ReportUiState.Ready(text, hasCrash = true), onGenerate = {}, onClearCrash = {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/14_report.png")
    }

    @Test
    fun insights() {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val wifi = NetworkKey("wifi", "")
        val cell = NetworkKey("cellular", "Telkomsel")
        val samples = buildList {
            listOf(120L, 140L, 110L, 160L, 130L).forEachIndexed { i, ms ->
                add(InsightSample.Latency(now - (4 - i) * day, wifi, "dns.adguard-dns.com", ms))
            }
            listOf(480L, 520L, 450L, 610L).forEachIndexed { i, ms ->
                add(InsightSample.Latency(now - (3 - i) * day, cell, "dns.adguard-dns.com", ms))
            }
            add(InsightSample.Latency(now - day, cell, "dns.adguard-dns.com", null))
            repeat(6) { add(InsightSample.NetworkCheck(now - it * day, wifi, CheckResult.OK, CheckResult.OK, CheckResult.OK)) }
            repeat(4) {
                add(InsightSample.NetworkCheck(now - it * day, cell, CheckResult.PROBLEM, CheckResult.OK, CheckResult.OK))
            }
        }
        compose.setContent {
            AetherDnsTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        InsightContent(
                            latency = InsightAnalyzer.latency(samples, now),
                            networks = InsightAnalyzer.networks(samples, now),
                            onCompare = {},
                            onClear = {}
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/15_insights.png")
    }
}
