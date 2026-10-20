package com.security.aetherdns

import com.security.aetherdns.data.ApplyError
import com.security.aetherdns.data.ApplyResult
import com.security.aetherdns.data.DnsApplier
import com.security.aetherdns.util.DnsManager
import com.security.aetherdns.util.DotProbeCause
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.DotProber
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DnsApplierTest {

    /** Mengembalikan hasil probe berurutan; panggilan melebihi daftar memakai hasil terakhir. */
    private class ScriptedProber(private vararg val results: DotProbeResult) : DotProber {
        var calls = 0
        override suspend fun probe(hostname: String): DotProbeResult =
            results[minOf(calls++, results.lastIndex)]
    }

    private val ok = DotProbeResult.Success(handshakeMs = 40, queryMs = 10)
    private val resolveFail = DotProbeResult.Failure(DotProbeCause.RESOLVE, "NXDOMAIN")
    private val tlsFail = DotProbeResult.Failure(DotProbeCause.TLS, "handshake")

    private fun brokenStrictMode() = FakeSecureSettings(mode = DnsManager.MODE_HOSTNAME, specifier = "dead.example.net")

    @Test
    fun appliesNormalizedHostnameWhenProbeSucceeds() = runTest {
        val settings = FakeSecureSettings()
        val result = DnsApplier(settings, ScriptedProber(ok), retryDelayMs = 0).apply("  DNS.AdGuard-DNS.com. ")

        assertEquals(ApplyResult.Success("dns.adguard-dns.com", ok), result)
        assertEquals(DnsManager.MODE_HOSTNAME, settings.mode)
        assertEquals("dns.adguard-dns.com", settings.specifier)
    }

    @Test
    fun rejectsWithoutPermissionAndDoesNotProbe() = runTest {
        val prober = ScriptedProber(ok)
        val result = DnsApplier(FakeSecureSettings(permission = false), prober, retryDelayMs = 0).apply("dns.adguard-dns.com")

        assertTrue(result is ApplyResult.Failure)
        assertEquals(0, prober.calls)
    }

    @Test
    fun rejectsInvalidHostnameWithoutTouchingSettings() = runTest {
        val settings = FakeSecureSettings()
        val result = DnsApplier(settings, ScriptedProber(ok), retryDelayMs = 0).apply("https://dns.adguard-dns.com")

        assertTrue(result is ApplyResult.Failure)
        assertTrue(settings.modeHistory.isEmpty())
    }

    @Test
    fun skipsProbeWhenDisabled() = runTest {
        val prober = ScriptedProber(tlsFail)
        val result = DnsApplier(FakeSecureSettings(), prober, retryDelayMs = 0).apply("dns.adguard-dns.com", probe = false)

        assertEquals(ApplyResult.Success("dns.adguard-dns.com", null), result)
        assertEquals(0, prober.calls)
    }

    @Test
    fun nonResolveFailureNeverTouchesSystemSettings() = runTest {
        val settings = brokenStrictMode()
        val result = DnsApplier(settings, ScriptedProber(tlsFail), retryDelayMs = 0).apply("dns.adguard-dns.com")

        assertTrue(result is ApplyResult.Failure)
        assertTrue(settings.modeHistory.isEmpty())
        assertEquals("dead.example.net", settings.specifier)
    }

    @Test
    fun resolveFailureOutsideStrictModeDoesNotSelfHeal() = runTest {
        val settings = FakeSecureSettings(mode = DnsManager.MODE_OPPORTUNISTIC)
        val result = DnsApplier(settings, ScriptedProber(resolveFail), retryDelayMs = 0).apply("dns.adguard-dns.com")

        assertTrue(result is ApplyResult.Failure)
        assertTrue(settings.modeHistory.isEmpty())
    }

    @Test
    fun selfHealingAppliesNewResolverWhenRetrySucceeds() = runTest {
        val settings = brokenStrictMode()
        val prober = ScriptedProber(resolveFail, resolveFail, ok)
        val result = DnsApplier(settings, prober, retryDelayMs = 0).apply("dns.adguard-dns.com")

        assertEquals(ApplyResult.Success("dns.adguard-dns.com", ok), result)
        assertEquals(3, prober.calls)
        assertEquals(listOf(DnsManager.MODE_OPPORTUNISTIC, DnsManager.MODE_HOSTNAME), settings.modeHistory)
        assertEquals("dns.adguard-dns.com", settings.specifier)
    }

    @Test
    fun selfHealingRestoresOriginalConfigWhenAllRetriesFail() = runTest {
        val settings = brokenStrictMode()
        val prober = ScriptedProber(resolveFail)
        val result = DnsApplier(settings, prober, retryDelayMs = 0, retryAttempts = 3).apply("dns.adguard-dns.com")

        assertTrue(result is ApplyResult.Failure)
        assertEquals(4, prober.calls)
        assertEquals(DnsManager.MODE_HOSTNAME, settings.mode)
        assertEquals("dead.example.net", settings.specifier)
    }

    @Test
    fun selfHealingRestoresOriginalConfigWhenFinalWriteIsRejected() = runTest {
        // Probe ulang berhasil, tetapi sistem menolak penulisan specifier baru
        val settings = brokenStrictMode().apply { rejectSpecifier = "dns.adguard-dns.com" }
        val result = DnsApplier(settings, ScriptedProber(resolveFail, ok), retryDelayMs = 0).apply("dns.adguard-dns.com")

        assertEquals(ApplyResult.Failure(ApplyError.WriteRejected), result)
        assertEquals(DnsManager.MODE_HOSTNAME, settings.mode)
        assertEquals("dead.example.net", settings.specifier)
    }

    @Test
    fun selfHealingRestoresOriginalConfigWhenCancelled() = runTest {
        val settings = brokenStrictMode()
        val retryStarted = CompletableDeferred<Unit>()
        val prober = object : DotProber {
            var calls = 0
            override suspend fun probe(hostname: String): DotProbeResult {
                if (calls++ == 0) return resolveFail
                retryStarted.complete(Unit)
                awaitCancellation()
            }
        }
        val applier = DnsApplier(settings, prober, retryDelayMs = 0)

        val job = async { applier.apply("dns.adguard-dns.com") }
        retryStarted.await()
        assertEquals(DnsManager.MODE_OPPORTUNISTIC, settings.mode)

        job.cancel()
        job.join()

        assertEquals(DnsManager.MODE_HOSTNAME, settings.mode)
        assertEquals("dead.example.net", settings.specifier)
    }
}
