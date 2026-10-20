package com.security.aetherdns.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.security.cert.CertificateException
import javax.net.ssl.*
import kotlin.random.Random

enum class DotProbeCause {
    RESOLVE,
    TIMEOUT,
    TLS,
    CERT_MISMATCH,
    DNS_QUERY,
    INVALID_INPUT,
    OTHER
}

sealed class DotProbeResult {
    /** [handshakeMs]: TCP connect + TLS handshake, [queryMs]: round-trip satu query DNS di atas TLS. */
    data class Success(val handshakeMs: Long, val queryMs: Long) : DotProbeResult() {
        val totalMs: Long get() = handshakeMs + queryMs
    }
    data class Failure(val cause: DotProbeCause, val reason: String) : DotProbeResult()
}

/** Abstraksi probe DoT agar logika apply dapat diuji di JVM dengan implementasi palsu. */
fun interface DotProber {
    suspend fun probe(hostname: String): DotProbeResult
}

object DotProbe : DotProber {
    private const val DOT_PORT = 853
    private const val TIMEOUT_MS = 3500

    /** Domain kanari yang tidak masuk blocklist resolver filtering mana pun. */
    private const val CANARY_DOMAIN = "example.com"

    /**
     * Pre-flight test resolver DoT:
     * 1. TCP connect + TLS handshake port 853 dengan SNI.
     * 2. Verifikasi rantai sertifikat X.509 dan kecocokan hostname (SAN / CN).
     * 3. Mengirim satu query DNS (A [CANARY_DOMAIN]) dengan framing RFC 7858 dan memvalidasi respons.
     * Langkah 3 memastikan server benar-benar melayani DNS, bukan sekadar menerima TLS di port 853.
     */
    override suspend fun probe(hostname: String): DotProbeResult = withContext(Dispatchers.IO) {
        val validation = HostnameValidator.validate(hostname)
        if (validation is HostnameValidator.Result.Invalid) {
            return@withContext DotProbeResult.Failure(DotProbeCause.INVALID_INPUT, validation.reason)
        }
        val targetFqdn = (validation as HostnameValidator.Result.Valid).fqdn

        var socket: SSLSocket? = null
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, null)
            val factory: SSLSocketFactory = sslContext.socketFactory

            socket = factory.createSocket() as SSLSocket
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
                return@withContext DotProbeResult.Failure(DotProbeCause.TLS, "Sesi TLS tidak valid")
            }

            // Validasi kecocokan hostname target dengan Subject Alternative Name (SAN) / Common Name (CN) di sertifikat
            val verifier = HttpsURLConnection.getDefaultHostnameVerifier()
            if (!verifier.verify(targetFqdn, session)) {
                return@withContext DotProbeResult.Failure(
                    DotProbeCause.CERT_MISMATCH,
                    "Sertifikat SSL/TLS yang diterima tidak cocok dengan hostname $targetFqdn"
                )
            }

            // Kirim satu query DNS di atas sesi TLS dan validasi respons
            val queryId = Random.nextInt(0, 0x10000)
            val queryStart = System.nanoTime()
            socket.outputStream.apply {
                write(DnsWireFormat.frame(DnsWireFormat.buildQuery(queryId, CANARY_DOMAIN)))
                flush()
            }
            val input = DataInputStream(socket.inputStream)
            val length = input.readUnsignedShort()
            val response = ByteArray(length).also { input.readFully(it) }
            val queryMs = (System.nanoTime() - queryStart) / 1_000_000

            val header = DnsWireFormat.parseHeader(response)
            when {
                header == null || !header.isResponse || header.id != queryId ->
                    DotProbeResult.Failure(DotProbeCause.DNS_QUERY, "Server port 853 tidak membalas dengan pesan DNS yang valid")
                header.rcode != DnsWireFormat.RCODE_NOERROR && header.rcode != DnsWireFormat.RCODE_NXDOMAIN ->
                    DotProbeResult.Failure(DotProbeCause.DNS_QUERY, "Resolver menolak query DNS (${rcodeName(header.rcode)})")
                else -> DotProbeResult.Success(handshakeMs, queryMs)
            }
        } catch (e: SSLException) {
            val rootCause = e.cause
            val message = if (rootCause is CertificateException) {
                "Sertifikat SSL/TLS tidak tepercaya atau kadaluarsa (${rootCause.message})"
            } else {
                "Handshake TLS gagal pada port 853: ${e.message}"
            }
            DotProbeResult.Failure(DotProbeCause.TLS, message)
        } catch (e: CertificateException) {
            DotProbeResult.Failure(DotProbeCause.TLS, "Sertifikat SSL/TLS server tidak valid atau kadaluarsa")
        } catch (e: java.net.SocketTimeoutException) {
            DotProbeResult.Failure(DotProbeCause.TIMEOUT, "Batas waktu terlampaui (Timeout): Server tidak merespons di port 853")
        } catch (e: java.net.UnknownHostException) {
            DotProbeResult.Failure(DotProbeCause.RESOLVE, "Hostname tidak dapat di-resolve (NXDOMAIN)")
        } catch (e: EOFException) {
            DotProbeResult.Failure(DotProbeCause.DNS_QUERY, "Server menutup koneksi sebelum membalas query DNS")
        } catch (e: Exception) {
            DotProbeResult.Failure(DotProbeCause.OTHER, "Gagal terhubung ke port 853: ${e.javaClass.simpleName} - ${e.message}")
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun rcodeName(rcode: Int): String = when (rcode) {
        DnsWireFormat.RCODE_SERVFAIL -> "SERVFAIL"
        DnsWireFormat.RCODE_REFUSED -> "REFUSED"
        else -> "RCODE $rcode"
    }
}
