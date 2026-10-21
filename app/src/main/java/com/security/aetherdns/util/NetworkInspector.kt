package com.security.aetherdns.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.random.Random

/** Hasil satu pemeriksaan jaringan. */
enum class CheckResult {
    /** Tidak ada masalah terdeteksi. */
    OK,
    /** Masalah terdeteksi (lihat [NetworkCheck] untuk artinya). */
    PROBLEM,
    /** Tidak bisa disimpulkan (mis. offline, port 53 ke luar diblokir total). */
    INCONCLUSIVE
}

enum class NetworkCheck {
    /** Kueri DNS biasa ke alamat yang tidak menjalankan DNS tetap dijawab: ISP menyadap port 53. */
    INTERCEPTION,
    /** Domain yang tidak ada dijawab dengan IP (bukan NXDOMAIN): jawaban DNS dipalsukan (iklan / halaman pengalihan). */
    NXDOMAIN_HIJACK,
    /** Handshake + kueri DNS-over-TLS port 853 berhasil: Private DNS bisa dipakai di jaringan ini. */
    DOT_REACHABLE
}

data class NetworkReport(val results: Map<NetworkCheck, CheckResult>, val timestamp: Long = System.currentTimeMillis()) {
    operator fun get(check: NetworkCheck): CheckResult = results[check] ?: CheckResult.INCONCLUSIVE

    /** ISP membelokkan DNS biasa dalam bentuk apa pun: alasan kuat memakai DNS terenkripsi. */
    val isPlainDnsTampered: Boolean
        get() = get(NetworkCheck.INTERCEPTION) == CheckResult.PROBLEM || get(NetworkCheck.NXDOMAIN_HIJACK) == CheckResult.PROBLEM
}

/** Probe DoT ke [hostname], opsional langsung ke IP [address] (melewati DNS sistem). */
fun interface DotAddressProber {
    suspend fun probe(hostname: String, address: String?): DotProbeResult
}

/** Kirim satu pesan DNS lewat UDP port 53 ke [server]; `null` bila tidak ada jawaban sampai batas waktu. */
fun interface PlainDnsTransport {
    fun exchange(server: String, message: ByteArray, timeoutMs: Int): ByteArray?
}

/**
 * Memeriksa bagaimana jaringan saat ini memperlakukan DNS, untuk menunjukkan manfaat DNS terenkripsi:
 * soket UDP aplikasi sendiri tidak melewati Private DNS, sehingga hasilnya mencerminkan perilaku ISP/jaringan.
 *
 * Kueri hanya ke domain netral (`example.com`, RFC 2606) dan alamat dokumentasi (RFC 5737); tidak ada data pengguna
 * yang dikirim.
 */
class NetworkInspector(
    private val transport: PlainDnsTransport = UdpDnsTransport,
    private val prober: DotAddressProber = DotAddressProber { host, address -> DotProbeClient.default.probeAt(host, address) },
    private val random: Random = Random.Default
) {
    suspend fun inspect(dotHostname: String): NetworkReport = coroutineScope {
        val interception = async(Dispatchers.IO) { checkInterception() }
        val hijack = async(Dispatchers.IO) { checkNxdomainHijack() }
        val dot = async { checkDot(dotHostname) }
        NetworkReport(
            mapOf(
                NetworkCheck.INTERCEPTION to interception.await(),
                NetworkCheck.NXDOMAIN_HIJACK to hijack.await(),
                NetworkCheck.DOT_REACHABLE to dot.await()
            )
        )
    }

    /**
     * 192.0.2.1 (TEST-NET-1) tidak pernah menjalankan DNS dan tidak dirutekan di internet. Jawaban DNS yang valid
     * hanya mungkin datang dari perangkat di jalur (proxy transparan ISP / router) yang menyadap port 53.
     */
    private fun checkInterception(): CheckResult {
        repeat(INTERCEPTION_ATTEMPTS) {
            val id = random.nextInt(0, 0x10000)
            val reply = transport.exchange(UNROUTABLE_SERVER, DnsWireFormat.buildQuery(id, NEUTRAL_DOMAIN), TIMEOUT_MS)
            val header = reply?.let { DnsWireFormat.parseHeader(it) }
            if (header != null && header.isResponse && header.id == id) return CheckResult.PROBLEM
        }
        return CheckResult.OK
    }

    /** Subdomain acak `example.com` pasti NXDOMAIN; jawaban berisi IP berarti dipalsukan di jalur. */
    private fun checkNxdomainHijack(): CheckResult {
        val name = "aetherdns-${random.nextLong().toULong().toString(36)}.$NEUTRAL_DOMAIN"
        var answered = false
        for (server in PUBLIC_RESOLVERS) {
            val id = random.nextInt(0, 0x10000)
            val reply = transport.exchange(server, DnsWireFormat.buildQuery(id, name), TIMEOUT_MS) ?: continue
            val response = DnsWireFormat.parseResponse(reply) ?: continue
            if (!response.header.isResponse || response.header.id != id) continue
            answered = true
            if (response.header.rcode == DnsWireFormat.RCODE_NOERROR && response.ipv4Answers.isNotEmpty()) {
                return CheckResult.PROBLEM
            }
        }
        return if (answered) CheckResult.OK else CheckResult.INCONCLUSIVE
    }

    private suspend fun checkDot(hostname: String): CheckResult {
        var result = prober.probe(hostname, null)
        if (result is DotProbeResult.Failure && result.cause == DotProbeCause.RESOLVE) {
            // DNS sistem mati (bisa karena Private DNS strict ke resolver yang diblokir): resolve sendiri lewat DNS
            // biasa lalu uji TLS langsung ke IP-nya, agar pemblokiran port 853 tetap terdeteksi
            val address = withContext(Dispatchers.IO) { resolvePlain(hostname) }
            if (address != null) result = prober.probe(hostname, address)
        }
        return classifyDot(result)
    }

    private fun resolvePlain(hostname: String): String? {
        for (server in PUBLIC_RESOLVERS) {
            val id = random.nextInt(0, 0x10000)
            val reply = transport.exchange(server, DnsWireFormat.buildQuery(id, hostname), TIMEOUT_MS) ?: continue
            val response = DnsWireFormat.parseResponse(reply) ?: continue
            if (response.header.isResponse && response.header.id == id) response.ipv4Answers.firstOrNull()?.let { return it }
        }
        return null
    }

    private fun classifyDot(result: DotProbeResult): CheckResult = when (result) {
        is DotProbeResult.Success -> CheckResult.OK
        is DotProbeResult.Failure -> when (result.cause) {
            // Port 853 diblokir / TLS diganggu di jaringan ini
            DotProbeCause.TIMEOUT, DotProbeCause.TLS, DotProbeCause.CERT_UNTRUSTED, DotProbeCause.DNS_QUERY ->
                CheckResult.PROBLEM
            // Offline atau masalah resolver tertentu, bukan perilaku jaringan
            else -> CheckResult.INCONCLUSIVE
        }
    }

    internal companion object {
        const val NEUTRAL_DOMAIN = "example.com"
        const val UNROUTABLE_SERVER = "192.0.2.1"
        val PUBLIC_RESOLVERS = listOf("8.8.8.8", "1.1.1.1")
        const val TIMEOUT_MS = 1500
        const val INTERCEPTION_ATTEMPTS = 2
    }
}

/** Transport UDP sungguhan; IP literal sehingga tidak ada resolusi nama sebelum kueri. */
object UdpDnsTransport : PlainDnsTransport {
    override fun exchange(server: String, message: ByteArray, timeoutMs: Int): ByteArray? = try {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val address = InetAddress.getByName(server)
            socket.send(DatagramPacket(message, message.size, address, 53))
            val buffer = ByteArray(1500)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            buffer.copyOf(packet.length)
        }
    } catch (_: SocketTimeoutException) {
        null
    } catch (_: Exception) {
        null
    }
}
