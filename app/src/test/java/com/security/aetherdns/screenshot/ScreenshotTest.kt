package com.security.aetherdns.screenshot

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.security.aetherdns.MainActivity
import com.security.aetherdns.R
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot layar utama untuk tinjauan UI (bukan uji regresi). Hanya berjalan dengan
 * `AETHERDNS_SCREENSHOTS=1`; hasil PNG ditulis ke `app/build/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// conscrypt-android (aktivasi bawaan) tidak punya library native untuk JVM desktop
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        assumeTrue(System.getenv("AETHERDNS_SCREENSHOTS") == "1")
        // Tanpa plugin Gradle Roborazzi, mode rekam diaktifkan lewat properti ini
        System.setProperty("roborazzi.test.record", "true")
        context.getSharedPreferences("onboarding", Context.MODE_PRIVATE).edit().putBoolean("completed", true).commit()
    }

    private fun protectionOn() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Global.putString(context.contentResolver, "private_dns_mode", "hostname")
        Settings.Global.putString(context.contentResolver, "private_dns_specifier", "dns.adguard-dns.com")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun launch(block: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { block() }
    }

    @Test
    fun homeActive() {
        protectionOn()
        launch { capture("1_home_active") }
    }

    @Test
    fun homeNeedsPermission() = launch { capture("2_home_needs_permission") }

    @Test
    fun resolversTab() {
        protectionOn()
        launch {
            compose.onNodeWithText(context.getString(R.string.tab_resolvers)).performClick()
            capture("3_resolvers")
        }
    }

    @Test
    fun moreTab() {
        protectionOn()
        launch {
            compose.onNodeWithText(context.getString(R.string.tab_more)).performClick()
            capture("4_more")
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun homeActiveDark() {
        protectionOn()
        launch { capture("5_home_active_dark") }
    }

    @Test
    @Config(qualifiers = "+night")
    fun homeNeedsPermissionDark() = launch { capture("6_home_needs_permission_dark") }

    @Test
    fun onboarding() {
        context.getSharedPreferences("onboarding", Context.MODE_PRIVATE).edit().clear().commit()
        launch { capture("7_onboarding") }
    }
}
