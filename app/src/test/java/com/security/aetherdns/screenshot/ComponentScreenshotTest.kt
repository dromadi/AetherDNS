package com.security.aetherdns.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.security.aetherdns.data.NextDnsSummary
import com.security.aetherdns.ui.AuditCard
import com.security.aetherdns.ui.BlockStatsCard
import com.security.aetherdns.ui.DiagnosticsCard
import com.security.aetherdns.ui.ReportUiState
import com.security.aetherdns.util.AuditItem
import com.security.aetherdns.util.AuditOutcome
import com.security.aetherdns.util.AuditSummary
import com.security.aetherdns.ui.CustomHostnameCard
import com.security.aetherdns.ui.DotBlockedDialog
import com.security.aetherdns.ui.NetworkCheckCard
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
    fun networkCheckStates() {
        val tampered = NetworkReport(
            mapOf(
                NetworkCheck.INTERCEPTION to CheckResult.PROBLEM,
                NetworkCheck.NXDOMAIN_HIJACK to CheckResult.PROBLEM,
                NetworkCheck.DOT_REACHABLE to CheckResult.OK
            )
        )
        val dotBlocked = NetworkReport(
            mapOf(
                NetworkCheck.INTERCEPTION to CheckResult.OK,
                NetworkCheck.NXDOMAIN_HIJACK to CheckResult.INCONCLUSIVE,
                NetworkCheck.DOT_REACHABLE to CheckResult.PROBLEM
            )
        )
        compose.setContent {
            AetherDnsTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        NetworkCheckCard(tampered, isChecking = false, isProtectionActive = true, onRun = {}, onPause = {})
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                        NetworkCheckCard(dotBlocked, isChecking = false, isProtectionActive = true, onRun = {}, onPause = {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/9_network_check.png")
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
                        AuditCard(summary = summary, isAuditing = false, onRunAudit = {})
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
}
