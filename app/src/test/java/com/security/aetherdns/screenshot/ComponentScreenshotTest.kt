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
import com.security.aetherdns.ui.NetworkCheckCard
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
}
