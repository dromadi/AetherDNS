package com.security.aetherdns.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.security.cert.CertificateException
import javax.net.ssl.*

enum class DotProbeCause {
    RESOLVE,
    TIMEOUT,
    TLS,
    CERT_MISMATCH,
    INVALID_INPUT,
    OTHER
}

sealed class DotProbeResult {
    data object Success : DotProbeResult()
    data class Failure(val cause: DotProbeCause, val reason: String) : DotProbeResult()
}

object DotProbe {
    private const val DOT_PORT = 853
    private const val TIMEOUT_MS = 3500

    /**
     * Melakukan uji koneksi TLS handshake ke server DoT port 853 dengan verifikasi sertifikat X.509,
     * Server Name Indication (SNI), dan pencocokan Hostname Verifier (SAN / CN).
     * Mencegah pemutusan total DNS sistem akibat memasukkan hostname yang tidak melayani DoT.
     */
    suspend fun probe(hostname: String): DotProbeResult = withContext(Dispatchers.IO) {
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
            socket.connect(InetSocketAddress(targetFqdn, DOT_PORT), TIMEOUT_MS)
            socket.startHandshake()

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

            DotProbeResult.Success
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
        } catch (e: Exception) {
            DotProbeResult.Failure(DotProbeCause.OTHER, "Gagal terhubung ke port 853: ${e.javaClass.simpleName} - ${e.message}")
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
