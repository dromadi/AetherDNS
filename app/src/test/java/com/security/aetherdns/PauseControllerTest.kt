package com.security.aetherdns

import com.security.aetherdns.data.ApplyResult
import com.security.aetherdns.data.DnsApplier
import com.security.aetherdns.data.PauseController
import com.security.aetherdns.data.PauseResult
import com.security.aetherdns.util.DnsManager
import com.security.aetherdns.util.DotProbeCause
import com.security.aetherdns.util.DotProbeResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseControllerTest {

    private var now = 1_000_000L
    private val minute = 60_000L

    private val settings = FakeSecureSettings(mode = DnsManager.MODE_HOSTNAME, specifier = "dns.adguard-dns.com")
    private val store = FakePauseStore()
    private val alarm = FakeResumeAlarm()
    // Probe selalu gagal: membuktikan resume TIDAK bergantung pada probe jaringan
    private val applier = DnsApplier(settings, { DotProbeResult.Failure(DotProbeCause.TIMEOUT, "offline") }, retryDelayMs = 0)
    private val controller = PauseController(settings, applier, store, alarm) { now }

    @Test
    fun pauseTurnsOffModeKeepsSpecifierAndSchedulesAlarm() {
        val result = controller.pause(15 * minute)

        assertEquals(PauseResult.Paused(now + 15 * minute), result)
        assertEquals(DnsManager.MODE_OFF, settings.mode)
        assertEquals("dns.adguard-dns.com", settings.specifier)
        assertEquals(now + 15 * minute, store.until)
        assertEquals(now + 15 * minute, alarm.scheduledAt)
    }

    @Test
    fun pauseRequiresActiveProtection() {
        settings.mode = DnsManager.MODE_OFF
        assertTrue(controller.pause(15 * minute) is PauseResult.Failure)
        assertNull(store.until)
        assertNull(alarm.scheduledAt)
    }

    @Test
    fun pauseRequiresPermission() {
        settings.permission = false
        assertTrue(controller.pause(15 * minute) is PauseResult.Failure)
        assertEquals(DnsManager.MODE_HOSTNAME, settings.mode)
    }

    @Test
    fun rejectedDisableLeavesNoPendingResume() {
        settings.rejectModes = setOf(DnsManager.MODE_OFF)
        assertTrue(controller.pause(15 * minute) is PauseResult.Failure)
        assertNull(store.until)
        assertNull(alarm.scheduledAt)
    }

    @Test
    fun dueAlarmRestoresProtectionWithoutProbe() = runTest {
        controller.pause(15 * minute)
        now += 16 * minute

        val result = controller.onAlarmOrBoot()

        assertEquals(ApplyResult.Success("dns.adguard-dns.com", null), result)
        assertEquals(DnsManager.MODE_HOSTNAME, settings.mode)
        assertNull(store.until)
    }

    @Test
    fun bootBeforeDueReschedulesInsteadOfResuming() = runTest {
        controller.pause(15 * minute)
        alarm.cancel() // alarm sistem hilang saat reboot
        now += 5 * minute

        assertNull(controller.onAlarmOrBoot())
        assertEquals(DnsManager.MODE_OFF, settings.mode)
        assertEquals(store.until, alarm.scheduledAt)
    }

    @Test
    fun manualChangeDuringPauseIsRespected() = runTest {
        controller.pause(15 * minute)
        settings.mode = DnsManager.MODE_OPPORTUNISTIC // pengguna mengubah lewat Settings HP
        now += 16 * minute

        assertNull(controller.onAlarmOrBoot())
        assertEquals(DnsManager.MODE_OPPORTUNISTIC, settings.mode)
        assertNull(store.until)
    }

    @Test
    fun alarmWithoutPauseDoesNothing() = runTest {
        assertNull(controller.onAlarmOrBoot())
        assertTrue(settings.modeHistory.isEmpty())
    }

    @Test
    fun resumeNowCancelsAlarmAndReapplies() = runTest {
        controller.pause(15 * minute)

        val result = controller.resumeNow()

        assertTrue(result is ApplyResult.Success)
        assertEquals(DnsManager.MODE_HOSTNAME, settings.mode)
        assertNull(store.until)
        assertNull(alarm.scheduledAt)
    }

    @Test
    fun resumeFallsBackToDefaultResolverWhenSpecifierMissing() = runTest {
        controller.pause(15 * minute)
        settings.specifier = ""

        assertEquals(ApplyResult.Success(DnsManager.DEFAULT_FALLBACK_HOSTNAME, null), controller.resumeNow())
    }
}
