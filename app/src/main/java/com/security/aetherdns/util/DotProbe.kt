package com.security.aetherdns.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.*
import kotlin.random.Random

enum class DotProbeCause {
    RESOLVE,
    TIMEOUT,
    TLS,
    CERT_UNTRUSTED,
    CERT_MISMATCH,
    DNS_QUERY,
    DNS_REFUSED,
    INVALID_INPUT,
    OTHER
}

sealed class DotProbeResult {
    /** [handshakeMs]: TCP connect + TLS handshake, [queryMs]: round-trip satu query DNS di atas TLS. */
    data class Success(val handshakeMs: Long, val queryMs: Long) : DotProbeResult() {
        val totalMs: Long get() = handshakeMs + queryMs
    }

    /** [detail]: keterangan teknis opsional (pesan exception / RCODE); teks utama dipetakan dari [cause] di UI. */
    data class Failure(val cause: DotProbeCause, val detail: String? = null) : DotProbeResult()
}

/** Hasil pemeriksaan filter: domain iklan uji yang diblokir / lolos / tidak dapat disimpulkan. */
data class FilterReport(
    val blocked: List<String>,
    val allowed: List<String>,
    val unknown: List<String>
) {
    val tested: Int get() = blocked.size + allowed.size
}

sealed class InspectionResult {
    data class Success(val latency: DotProbeResult.Success, val filter: FilterReport) : InspectionResult()
    data class Failure(val failure: DotProbeResult.Failure) : InspectionResult()
}

/** Abstraksi probe DoT agar logika apply dapat diuji di JVM dengan implementasi palsu. */
fun interface DotProber {
    suspend fun probe(hostname: String): DotProbeResult
}

/** Latensi + pratinjau filter sebuah resolver DoT sebelum diterapkan. */
fun interface ResolverInspector {
    suspend fun inspect(hostname: String): InspectionResult
}

/** Instance default yang dipakai aplikasi (hostname verifier bawaan Android). */
object DotProbe : DotProber by DotProbeClient.default, ResolverInspector by DotProbeClient.default

/**
 * Klien DoT. [hostnameVerifier] dapat diganti untuk pengujian di JVM desktop, karena di sana
 * `HttpsURLConnection.getDefaultHostnameVerifier()` selalu menolak (JDK memverifikasi hostname di tempat lain);
 * di Android verifier default memeriksa SAN/CN sertifikat dengan benar.
 */
class DotProbeClient(
    private val hostnameVerifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
) : DotProber, ResolverInspector {

    companion object {
        private const val DOT_PORT = 853
        private const val TIMEOUT_MS = 3500

        /** Domain kanari yang tidak masuk blocklist resolver filtering mana pun. */
        private const val CANARY_DOMAIN = "example.com"

        internal val default by lazy { DotProbeClient() }

        private fun rcodeName(rcode: Int): String = when (rcode) {
            DnsWireFormat.RCODE_SERVFAIL -> "SERVFAIL"
            DnsWireFormat.RCODE_REFUSED -> "REFUSED"
            else -> "RCODE $rcode"
        }
    }

    /**
     * Pre-flight test resolver DoT:
     * 1. TCP connect + TLS handshake port 853 dengan SNI.
     * 2. Verifikasi rantai sertifikat X.509 dan kecocokan hostname (SAN / CN).
     * 3. Mengirim satu query DNS (A [CANARY_DOMAIN]) dengan framing RFC 7858 dan memvalidasi respons.
     * Langkah 3 memastikan server benar-benar melayani DNS, bukan sekadar menerima TLS di port 853.
     */
    override suspend fun probe(hostname: String): DotProbeResult =
        withSession(hostname, onFailure = { it }) { session -> session.canary() }

    /**
     * Seperti [probe], lalu memakai koneksi TLS yang sama untuk meng-query [AdTestDomains] dan
     * menilai apakah resolver memblokirnya (NXDOMAIN / NODATA / REFUSED / 0.0.0.0).
     */
    override suspend fun inspect(hostname: String): InspectionResult =
        withSession(hostname, onFailure = { InspectionResult.Failure(it) }) { session ->
            when (val latency = session.canary()) {
                is DotProbeResult.Failure -> InspectionResult.Failure(latency)
                is DotProbeResult.Success -> {
                    val verdicts = AdTestDomains.domains.associateWith { domain ->
                        FilterVerdict.of(session.query(domain))
                    }
                    InspectionResult.Success(
                        latency,
                        FilterReport(
                            blocked = verdicts.filterValues { it == FilterVerdict.Verdict.BLOCKED }.keys.toList(),
                            allowed = verdicts.filterValues { it == FilterVerdict.Verdict.ALLOWED }.keys.toList(),
                            unknown = verdicts.filterValues { it == FilterVerdict.Verdict.UNKNOWN }.keys.toList()
                        )
                    )
                }
            }
        }

    /** Koneksi DoT yang sudah lolos handshake dan verifikasi hostname. */
    private class Session(private val socket: SSLSocket, val handshakeMs: Long) {
        private val input = DataInputStream(socket.inputStream)

        fun query(name: String): DnsWireFormat.Response {
            val id = Random.nextInt(0, 0x10000)
            socket.outputStream.apply {
                write(DnsWireFormat.frame(DnsWireFormat.buildQuery(id, name)))
                flush()
            }
            val length = input.readUnsignedShort()
            val message = ByteArray(length).also { input.readFully(it) }
            val response = DnsWireFormat.parseResponse(message)
            if (response == null || !response.header.isResponse || response.header.id != id) {
                throw DnsProtocolException()
            }
            return response
        }

        fun canary(): DotProbeResult {
            val start = System.nanoTime()
            val response = query(CANARY_DOMAIN)
            val queryMs = (System.nanoTime() - start) / 1_000_000
            val rcode = response.header.rcode
            return if (rcode == DnsWireFormat.RCODE_NOERROR || rcode == DnsWireFormat.RCODE_NXDOMAIN) {
                DotProbeResult.Success(handshakeMs, queryMs)
            } else {
                DotProbeResult.Failure(DotProbeCause.DNS_REFUSED, rcodeName(rcode))
            }
        }
    }

    private class DnsProtocolException : IOException()

    /** Membuka sesi DoT terverifikasi, menjalankan [block], dan memetakan setiap kegagalan ke [DotProbeCause]. */
    private suspend fun <T> withSession(
        hostname: String,
        onFailure: (DotProbeResult.Failure) -> T,
        block: (Session) -> T
    ): T = withContext(Dispatchers.IO) {
        val validation = HostnameValidator.validate(hostname)
        if (validation is HostnameValidator.Result.Invalid) {
            return@withContext onFailure(DotProbeResult.Failure(DotProbeCause.INVALID_INPUT, validation.error.name))
        }
        val targetFqdn = (validation as HostnameValidator.Result.Valid).fqdn

        var socket: SSLSocket? = null
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, null)
            socket = sslContext.socketFactory.createSocket() as SSLSocket
            socket.soTimeout = TIMEOUT_MS

            // Enable SNI (Server Name Indication)
            val sslParams = socket.sslParameters
            sslParams.serverNames = listOf(SNIHostName(targetFqdn))
            socket.sslParameters = sslParams

            // Connect & perform TLS handshake
            val handshakeStart = System.nanoTime()
            socket.connect(InetSocketAddress(targetFqdn, DOT_PORT), TIMEOUT_MS)
            socket.startHandshake()
            val handshakeMs = (System.nanoTime() - handshakeStart) / 1_000_000

            val session = socket.session
            if (!session.isValid) {
                return@withContext onFailure(DotProbeResult.Failure(DotProbeCause.TLS))
            }

            // Validasi kecocokan hostname target dengan Subject Alternative Name (SAN) / Common Name (CN) di sertifikat
            if (!hostnameVerifier.verify(targetFqdn, session)) {
                return@withContext onFailure(DotProbeResult.Failure(DotProbeCause.CERT_MISMATCH, targetFqdn))
            }

            block(Session(socket, handshakeMs))
        } catch (e: SSLException) {
            val cause = if (e.cause is CertificateException) DotProbeCause.CERT_UNTRUSTED else DotProbeCause.TLS
            onFailure(DotProbeResult.Failure(cause, (e.cause ?: e).message))
        } catch (e: CertificateException) {
            onFailure(DotProbeResult.Failure(DotProbeCause.CERT_UNTRUSTED, e.message))
        } catch (e: SocketTimeoutException) {
            onFailure(DotProbeResult.Failure(DotProbeCause.TIMEOUT))
        } catch (e: UnknownHostException) {
            onFailure(DotProbeResult.Failure(DotProbeCause.RESOLVE))
        } catch (e: EOFException) {
            onFailure(DotProbeResult.Failure(DotProbeCause.DNS_QUERY))
        } catch (e: DnsProtocolException) {
            onFailure(DotProbeResult.Failure(DotProbeCause.DNS_QUERY))
        } catch (e: Exception) {
            onFailure(DotProbeResult.Failure(DotProbeCause.OTHER, "${e.javaClass.simpleName}: ${e.message}"))
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
