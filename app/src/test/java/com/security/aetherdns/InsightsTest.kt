package com.security.aetherdns

import com.security.aetherdns.data.FileInsightStore
import com.security.aetherdns.data.InsightAnalyzer
import com.security.aetherdns.data.InsightCodec
import com.security.aetherdns.data.InsightSample
import com.security.aetherdns.data.NetworkKey
import com.security.aetherdns.data.SpeedLevel
import com.security.aetherdns.util.CheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InsightsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private val wifi = NetworkKey("wifi", "Telkomsel")
    private val tsel = NetworkKey("cellular", "Telkomsel")

    private fun lat(t: Long, ms: Long?, net: NetworkKey = wifi, resolver: String = "dns.adguard-dns.com") =
        InsightSample.Latency(t, net, resolver, ms)

    @Test
    fun `codec round trips and skips broken lines`() {
        val samples = listOf(
            lat(1, 42), lat(2, null, tsel),
            InsightSample.NetworkCheck(3, tsel, CheckResult.PROBLEM, CheckResult.OK, CheckResult.INCONCLUSIVE)
        )
        samples.forEach { assertEquals(it, InsightCodec.decode(InsightCodec.encode(it))) }
        assertNull(InsightCodec.decode("garbage"))
        assertNull(InsightCodec.decode("N\t1\twifi\t\tNOPE\tOK\tOK"))
    }

    @Test
    fun `latency is grouped per resolver and network, wifi ignores carrier`() {
        val samples = listOf(
            lat(now - 3 * day, 900), lat(now - 2 * day, 1000), lat(now - day, 1100, NetworkKey("wifi", "XL")),
            lat(now - day, null),
            lat(now - day, 50, tsel), lat(now - 1, 70, tsel)
        )
        val result = InsightAnalyzer.latency(samples, now)
        val wifiSummary = result.first { it.network.transport == "wifi" }
        assertEquals(1000L, wifiSummary.medianMs)
        assertEquals(3, wifiSummary.samples)
        assertEquals(1, wifiSummary.failures)
        assertEquals(listOf(900L, 1000L, 1100L), wifiSummary.daily)
        assertEquals(SpeedLevel.SLOW, wifiSummary.level)
        assertEquals(SpeedLevel.FAST, result.first { it.network == tsel }.level)
    }

    @Test
    fun `old samples fall outside the window`() {
        val result = InsightAnalyzer.latency(listOf(lat(now - 8 * day, 10), lat(now - day, 30)), now)
        assertEquals(1, result.single().samples)
    }

    @Test
    fun `current needs enough samples`() {
        val two = listOf(lat(now - 2, 900), lat(now - 1, 900))
        assertNull(InsightAnalyzer.current(two, "dns.adguard-dns.com", wifi, now))
        val three = two + lat(now, 900)
        assertEquals(900L, InsightAnalyzer.current(three, "dns.adguard-dns.com", NetworkKey("wifi", "other"), now)?.medianMs)
    }

    @Test
    fun `network report counts tampering and blocked encrypted dns per network`() {
        val checks = listOf(
            InsightSample.NetworkCheck(now - 1, tsel, CheckResult.PROBLEM, CheckResult.OK, CheckResult.OK),
            InsightSample.NetworkCheck(now - 2, tsel, CheckResult.OK, CheckResult.PROBLEM, CheckResult.PROBLEM),
            InsightSample.NetworkCheck(now - 3, wifi, CheckResult.OK, CheckResult.OK, CheckResult.OK)
        )
        val result = InsightAnalyzer.networks(checks, now)
        val cell = result.first { it.network == tsel }
        assertEquals(2, cell.checks)
        assertEquals(2, cell.tampered)
        assertEquals(1, cell.dotBlocked)
        assertEquals(0, result.first { it.network.transport == "wifi" }.tampered)
    }

    @Test
    fun `file store keeps recent samples only and can be cleared`() {
        var clock = now
        val store = FileInsightStore(tmp.root) { clock }
        store.append(lat(now - 40 * day, 1))
        store.append(lat(now, 2))
        assertEquals(listOf(2L), store.all().map { (it as InsightSample.Latency).totalMs })
        store.clear()
        assertTrue(store.all().isEmpty())
    }
}
