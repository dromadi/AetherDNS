package com.security.aetherdns

import com.security.aetherdns.util.DotProbeCause
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.StabilityAdvice
import com.security.aetherdns.util.StabilityResult
import com.security.aetherdns.util.StabilityTest
import com.security.aetherdns.util.StabilityVerdict
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StabilityTestTest {

    @Test
    fun `verdicts follow failures and latency`() {
        assertEquals(StabilityVerdict.STABLE, StabilityResult("a", listOf(80, 90, 100, 120), 0).verdict)
        assertEquals(StabilityVerdict.SLOW, StabilityResult("a", listOf(450, 480, 500, 520), 0).verdict)
        assertEquals(StabilityVerdict.UNSTABLE, StabilityResult("a", listOf(80, 90, 100), 1).verdict)
        // Lonjakan besar di sebagian percobaan juga dianggap tidak stabil
        assertEquals(StabilityVerdict.UNSTABLE, StabilityResult("a", listOf(100, 100, 100, 100, 100, 100, 100, 100, 2000, 2500), 0).verdict)
        assertEquals(StabilityVerdict.UNSTABLE, StabilityResult("a", emptyList(), 5).verdict)
    }

    @Test
    fun `median and worst`() {
        val r = StabilityResult("a", listOf(300, 100, 200, 900), 0)
        assertEquals(200L, r.medianMs)
        assertEquals(900L, r.worstMs)
        assertNull(StabilityResult("a", emptyList(), 2).medianMs)
    }

    @Test
    fun `runs every resolver each round and reports progress`() = runTest {
        var calls = 0
        val prober = com.security.aetherdns.util.DotProber { host ->
            calls++
            if (host == "flaky" && calls % 3 == 0) DotProbeResult.Failure(DotProbeCause.TIMEOUT) else DotProbeResult.Success(40, 60)
        }
        val progress = mutableListOf<Int>()
        val pauses = mutableListOf<Long>()
        val results = StabilityTest(prober, rounds = 4, intervalMs = 10, pause = { pauses += it })
            .run(listOf("good", "flaky")) { progress += it }
        assertEquals(listOf(1, 2, 3, 4), progress)
        assertEquals(3, pauses.size)
        assertEquals(listOf("good", "flaky"), results.map { it.resolver })
        assertEquals(8, results.sumOf { it.attempts })
        assertEquals(4, results.first { it.resolver == "good" }.attempts)
    }

    private fun stable(host: String, ms: Long = 100) = StabilityResult(host, List(12) { ms }, 0)
    private fun flaky(host: String) = StabilityResult(host, List(9) { 500L }, 3)

    @Test
    fun `advice picks the right conclusion`() {
        val ref = "1dot1dot1dot1.cloudflare-dns.com"
        val alts = setOf("ag", "cd", "mv")
        assertEquals(StabilityAdvice.ActiveStable, StabilityAdvice.of(listOf(stable("ag"), flaky(ref)), "ag", alts, ref))
        assertEquals(StabilityAdvice.NetworkProblem, StabilityAdvice.of(listOf(flaky("ag"), stable("cd"), flaky(ref)), "ag", alts, ref))
        assertEquals(
            StabilityAdvice.SwitchTo("cd", 90),
            StabilityAdvice.of(listOf(flaky("ag"), stable("cd", 90), stable("mv", 140), stable(ref)), "ag", alts, ref)
        )
        assertEquals(StabilityAdvice.NoAlternative, StabilityAdvice.of(listOf(flaky("ag"), flaky("cd"), stable(ref)), "ag", alts, ref))
        // Resolver aktif = pembanding: tidak disimpulkan sebagai masalah jaringan
        assertEquals(StabilityAdvice.NoAlternative, StabilityAdvice.of(listOf(flaky(ref)), ref, emptySet(), ref))
    }
}
