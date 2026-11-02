package com.security.aetherdns.live

import com.security.aetherdns.util.DotProbeCause
import com.security.aetherdns.util.DotProbeClient
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.CheckResult
import com.security.aetherdns.util.DotAddressProber
import com.security.aetherdns.util.InspectionResult
import com.security.aetherdns.util.NetworkCheck
import com.security.aetherdns.util.NetworkInspector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier

/**
 * Uji terhadap resolver DoT sungguhan (butuh akses jaringan ke port 853).
 * Hanya berjalan bila env `AETHERDNS_LIVE_DOT=1`; dijalankan oleh job CI `live-dot`, tidak di build biasa.
 *
 * Ambang dibuat longgar agar perubahan blocklist resolver tidak membuat test rapuh; yang diuji adalah
 * bahwa handshake, verifikasi hostname, query DNS, dan penilaian filter bekerja end-to-end.
 */
class LiveDotProbeTest {

    companion object {
        private const val CONNECT_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2_000L

        @BeforeClass
        @JvmStatic
        fun requireLiveNetwork() {
            assumeTrue("Set AETHERDNS_LIVE_DOT=1 untuk menjalankan uji DoT live", System.getenv("AETHERDNS_LIVE_DOT") == "1")
        }
    }

    /**
     * Di JVM desktop verifier default `HttpsURLConnection` selalu menolak, jadi test memakai pencocokan SAN DNS
     * sederhana (termasuk wildcard satu label) — setara dengan yang dilakukan verifier Android.
     */
    private val sanVerifier = HostnameVerifier { hostname, session ->
        val cert = session.peerCertificates.firstOrNull() as? X509Certificate ?: return@HostnameVerifier false
        val dnsNames = cert.subjectAlternativeNames.orEmpty()
            .filter { it[0] == 2 }
            .map { (it[1] as String).lowercase() }
        dnsNames.any { pattern ->
            pattern == hostname ||
                (pattern.startsWith("*.") && hostname.substringAfter('.') == pattern.removePrefix("*.") && hostname.count { it == '.' } == pattern.count { it == '.' })
        }
    }

    /** Verifier yang selalu menolak: memastikan kegagalan verifikasi hostname dipetakan ke CERT_MISMATCH. */
    private val rejectAll = HostnameVerifier { _, _ -> false }

    private val client = DotProbeClient(sanVerifier)

    /**
     * Menambahkan satu baris tabel Markdown ke file di env `AETHERDNS_LIVE_REPORT` (bila diset),
     * sebelum assertion, sehingga resolver yang gagal pun tercatat di ringkasan job CI.
     */
    private fun report(host: String, result: InspectionResult) {
        val path = System.getenv("AETHERDNS_LIVE_REPORT") ?: return
        val row = when (result) {
            is InspectionResult.Success -> with(result) {
                "| `$host` | ✅ | ${latency.totalMs} ms (TLS ${latency.handshakeMs} + query ${latency.queryMs}) | " +
                    "${filter.blocked.size}/${filter.tested} diblokir" +
                    (if (filter.unknown.isNotEmpty()) ", ${filter.unknown.size} tidak pasti" else "") + " |"
            }
            is InspectionResult.Failure -> "| `$host` | ❌ | — | ${result.failure.cause}${result.failure.detail?.let { ": $it" } ?: ""} |"
        }
        File(path).apply { parentFile?.mkdirs() }.appendText(row + "\n")
    }

    /**
     * Resolver publik dari runner CI sesekali gagal tersambung sesaat (mis. Quad9); koneksi dicoba ulang hingga
     * [CONNECT_ATTEMPTS] kali sebelum dianggap gagal. Penilaian filter tetap ketat pada hasil yang berhasil.
     */
    private fun successOf(host: String): InspectionResult.Success {
        var result = runBlocking { client.inspect(host) }
        repeat(CONNECT_ATTEMPTS - 1) {
            if (result is InspectionResult.Success) return@repeat
            Thread.sleep(RETRY_DELAY_MS)
            result = runBlocking { client.inspect(host) }
        }
        report(host, result)
        assertTrue("$host: $result", result is InspectionResult.Success)
        return result as InspectionResult.Success
    }

    @Test
    fun adguardBlocksAdDomains() {
        val result = successOf("dns.adguard-dns.com")
        println("AdGuard: ${result.latency.totalMs} ms, filter=${result.filter}")
        assertTrue("AdGuard harus memblokir mayoritas domain iklan: ${result.filter}", result.filter.blocked.size >= 3)
    }

    @Test
    fun adguardFamilyBlocksAdDomains() {
        val result = successOf("family.adguard-dns.com")
        println("AdGuard Family: ${result.latency.totalMs} ms, filter=${result.filter}")
        assertTrue(result.filter.toString(), result.filter.blocked.size >= 3)
    }

    @Test
    fun mullvadAdblockBlocksSomeAdDomains() {
        val result = successOf("adblock.dns.mullvad.net")
        println("Mullvad: ${result.latency.totalMs} ms, filter=${result.filter}")
        assertTrue(result.filter.toString(), result.filter.blocked.size >= 2)
    }

    @Test
    fun cloudflareSecurityDoesNotBlockAds() {
        val result = successOf("security.cloudflare-dns.com")
        println("Cloudflare Security: ${result.latency.totalMs} ms, filter=${result.filter}")
        assertTrue("Cloudflare Security bukan ad-blocker: ${result.filter}", result.filter.allowed.size >= 3)
    }

    @Test
    fun controlDFreeBlocksAdDomains() {
        val result = successOf("p2.freedns.controld.com")
        println("Control D Free: ${result.latency.totalMs} ms, filter=${result.filter}")
        assertTrue(result.filter.toString(), result.filter.blocked.size >= 2)
    }

    @Test
    fun mullvadExtendedBlocksAdDomains() {
        val result = successOf("extended.dns.mullvad.net")
        println("Mullvad Extended: ${result.latency.totalMs} ms, filter=${result.filter}")
        assertTrue(result.filter.toString(), result.filter.blocked.size >= 2)
    }

    @Test
    fun mullvadAllBlocksAdDomains() {
        val result = successOf("all.dns.mullvad.net")
        println("Mullvad All: ${result.latency.totalMs} ms, filter=${result.filter}")
        assertTrue(result.filter.toString(), result.filter.blocked.size >= 2)
    }

    @Test
    fun quad9DoesNotBlockAds() {
        val result = successOf("dns.quad9.net")
        println("Quad9: ${result.latency.totalMs} ms, filter=${result.filter}")
        assertTrue("Quad9 bukan ad-blocker: ${result.filter}", result.filter.allowed.size >= 3)
    }

    @Test
    fun cloudflareFamiliesDoesNotBlockAds() {
        val result = successOf("family.cloudflare-dns.com")
        println("Cloudflare Families: ${result.latency.totalMs} ms, filter=${result.filter}")
        assertTrue("Cloudflare Families bukan ad-blocker: ${result.filter}", result.filter.allowed.size >= 3)
    }

    @Test
    fun cloudflarePlainDoesNotFilter() {
        val result = successOf("1dot1dot1dot1.cloudflare-dns.com")
        println("Cloudflare 1.1.1.1: ${result.latency.totalMs} ms, filter=${result.filter}")
        assertTrue("Cloudflare 1.1.1.1 tidak memfilter: ${result.filter}", result.filter.allowed.size >= 3)
    }

    @Test
    fun adguardUnfilteredDoesNotFilter() {
        val result = successOf("unfiltered.adguard-dns.com")
        println("AdGuard Non-filtering: ${result.latency.totalMs} ms, filter=${result.filter}")
        assertTrue("AdGuard Non-filtering tidak memfilter: ${result.filter}", result.filter.allowed.size >= 3)
    }

    @Test
    fun googleDoesNotFilter() {
        val result = successOf("dns.google")
        println("Google: ${result.latency.totalMs} ms, filter=${result.filter}")
        assertTrue("Google tidak memfilter: ${result.filter}", result.filter.allowed.size >= 3)
    }

    @Test
    fun nextdnsAnycastAnswersQueries() {
        val result = successOf("dns.nextdns.io")
        println("NextDNS: ${result.latency.totalMs} ms, filter=${result.filter}")
    }

    @Test
    fun probeReportsLatency() {
        val result = runBlocking { client.probe("dns.adguard-dns.com") }
        assertTrue("$result", result is DotProbeResult.Success)
        result as DotProbeResult.Success
        assertTrue(result.handshakeMs > 0 && result.queryMs >= 0)
    }

    @Test
    fun hostWithoutDotIsRejected() {
        // example.com tidak melayani port 853: koneksi ditolak / timeout, tidak boleh dianggap resolver valid
        val result = runBlocking { client.probe("example.com") }
        assertTrue("$result", result is DotProbeResult.Failure)
        val cause = (result as DotProbeResult.Failure).cause
        assertTrue("$cause", cause == DotProbeCause.TIMEOUT || cause == DotProbeCause.OTHER || cause == DotProbeCause.TLS)
    }

    @Test
    fun unresolvableHostReportsResolveFailure() {
        val result = runBlocking { client.probe("aetherdns-does-not-exist.invalid") }
        assertEquals(DotProbeResult.Failure(DotProbeCause.RESOLVE), result)
    }

    @Test
    fun failedHostnameVerificationIsCertMismatch() {
        val result = runBlocking { DotProbeClient(rejectAll).probe("dns.adguard-dns.com") }
        assertTrue("$result", result is DotProbeResult.Failure)
        assertEquals(DotProbeCause.CERT_MISMATCH, (result as DotProbeResult.Failure).cause)
    }

    /**
     * Jaringan runner CI (pusat data) tidak membelokkan DNS: Cek jaringan harus melaporkan bersih dan DoT terjangkau.
     * Menguji transport UDP sungguhan, penyusunan kueri, dan klasifikasi end-to-end.
     */
    @Test
    fun networkInspectorReportsCleanDatacenterNetwork() = runBlocking {
        val inspector = NetworkInspector(prober = DotAddressProber { host, address -> client.probeAt(host, address) })
        val report = inspector.inspect("dns.adguard-dns.com")
        assertEquals(CheckResult.OK, report[NetworkCheck.INTERCEPTION])
        assertEquals(CheckResult.OK, report[NetworkCheck.NXDOMAIN_HIJACK])
        assertEquals(CheckResult.OK, report[NetworkCheck.DOT_REACHABLE])
    }
}
