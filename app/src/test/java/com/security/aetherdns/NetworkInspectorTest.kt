package com.security.aetherdns

import com.security.aetherdns.util.CheckResult
import com.security.aetherdns.util.DotProbeCause
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.DotAddressProber
import com.security.aetherdns.util.NetworkCheck
import com.security.aetherdns.util.NetworkInspector
import com.security.aetherdns.util.PlainDnsTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class NetworkInspectorTest {

    /** Membuat jawaban dari kueri: menyalin ID + pertanyaan, menyetel QR/RCODE, dan menambah record A opsional. */
    private fun reply(query: ByteArray, rcode: Int, answers: List<String> = emptyList(), idOverride: Int? = null): ByteArray {
        val out = ByteArrayOutputStream()
        val id = idOverride ?: (((query[0].toInt() and 0xFF) shl 8) or (query[1].toInt() and 0xFF))
        out.write(id shr 8); out.write(id and 0xFF)
        out.write(0x81); out.write(0x80 or rcode)
        out.write(0); out.write(1) // QDCOUNT
        out.write(0); out.write(answers.size)
        repeat(4) { out.write(0) } // NSCOUNT, ARCOUNT
        out.write(query, 12, query.size - 12) // pertanyaan
        answers.forEach { ip ->
            out.write(0xC0); out.write(0x0C) // pointer ke nama pertanyaan
            out.write(0); out.write(1) // TYPE A
            out.write(0); out.write(1) // CLASS IN
            repeat(3) { out.write(0) }; out.write(60) // TTL
            out.write(0); out.write(4)
            ip.split('.').forEach { out.write(it.toInt()) }
        }
        return out.toByteArray()
    }

    private val dotOk = DotAddressProber { _, _ -> DotProbeResult.Success(10, 10) }

    private fun inspector(transport: PlainDnsTransport, prober: DotAddressProber = dotOk) =
        NetworkInspector(transport, prober, Random(42))

    @Test
    fun `clean network reports no tampering`() = runTest {
        val report = inspector({ server, query, _ ->
            if (server == "192.0.2.1") null else reply(query, rcode = 3)
        }).inspect("dns.adguard-dns.com")

        assertEquals(CheckResult.OK, report[NetworkCheck.INTERCEPTION])
        assertEquals(CheckResult.OK, report[NetworkCheck.NXDOMAIN_HIJACK])
        assertEquals(CheckResult.OK, report[NetworkCheck.DOT_REACHABLE])
        assertFalse(report.isPlainDnsTampered)
    }

    @Test
    fun `answer from an unroutable address means port 53 is intercepted`() = runTest {
        val report = inspector({ _, query, _ -> reply(query, rcode = 0, answers = listOf("93.184.215.14")) })
            .inspect("dns.adguard-dns.com")

        assertEquals(CheckResult.PROBLEM, report[NetworkCheck.INTERCEPTION])
        assertTrue(report.isPlainDnsTampered)
    }

    @Test
    fun `reply with a foreign id is not counted as interception`() = runTest {
        val report = inspector({ server, query, _ ->
            if (server == "192.0.2.1") reply(query, rcode = 0, idOverride = 1) else reply(query, rcode = 3)
        }).inspect("x.example")

        assertEquals(CheckResult.OK, report[NetworkCheck.INTERCEPTION])
    }

    @Test
    fun `ip for a nonexistent name means nxdomain hijacking`() = runTest {
        val report = inspector({ server, query, _ ->
            if (server == "192.0.2.1") null else reply(query, rcode = 0, answers = listOf("36.86.63.182"))
        }).inspect("dns.adguard-dns.com")

        assertEquals(CheckResult.OK, report[NetworkCheck.INTERCEPTION])
        assertEquals(CheckResult.PROBLEM, report[NetworkCheck.NXDOMAIN_HIJACK])
        assertTrue(report.isPlainDnsTampered)
    }

    @Test
    fun `no plain dns answers at all is inconclusive`() = runTest {
        val report = inspector({ _, _, _ -> null }).inspect("dns.adguard-dns.com")

        assertEquals(CheckResult.OK, report[NetworkCheck.INTERCEPTION])
        assertEquals(CheckResult.INCONCLUSIVE, report[NetworkCheck.NXDOMAIN_HIJACK])
    }

    @Test
    fun `blocked port 853 is a problem but offline is inconclusive`() = runTest {
        val transport = PlainDnsTransport { _, _, _ -> null }
        val blocked = inspector(transport, { _, _ -> DotProbeResult.Failure(DotProbeCause.TIMEOUT) }).inspect("h")
        val offline = inspector(transport, { _, _ -> DotProbeResult.Failure(DotProbeCause.RESOLVE) }).inspect("h")

        assertEquals(CheckResult.PROBLEM, blocked[NetworkCheck.DOT_REACHABLE])
        assertEquals(CheckResult.INCONCLUSIVE, offline[NetworkCheck.DOT_REACHABLE])
    }

    @Test
    fun `blocked port 853 is still detected when system dns is down`() = runTest {
        // Private DNS strict ke resolver yang diblokir: DNS sistem gagal (RESOLVE), DNS biasa ke luar masih jalan
        val probedAddresses = mutableListOf<String?>()
        val prober = DotAddressProber { _, address ->
            probedAddresses += address
            if (address == null) DotProbeResult.Failure(DotProbeCause.RESOLVE) else DotProbeResult.Failure(DotProbeCause.TIMEOUT)
        }
        val report = inspector({ server, query, _ ->
            when {
                server == "192.0.2.1" -> null
                query.size > 40 -> reply(query, rcode = 3) // subdomain acak example.com
                else -> reply(query, rcode = 0, answers = listOf("94.140.14.14"))
            }
        }, prober).inspect("dns.adguard-dns.com")

        assertEquals(listOf(null, "94.140.14.14"), probedAddresses)
        assertEquals(CheckResult.PROBLEM, report[NetworkCheck.DOT_REACHABLE])
    }
}
