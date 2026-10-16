package com.security.aetherdns.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.security.cert.CertificateException
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

sealed class DotProbeResult {
    data object Success : DotProbeResult()
    data class Failure(val reason: String) : DotProbeResult()
}

object DotProbe {
    private const val DOT_PORT = 853
    private const val TIMEOUT_MS = 3500

    /**
     * Melakukan uji koneksi TLS handshake ke server DoT port 853 dengan verifikasi sertifikat X.509 dan SNI.
     * Mencegah pemutusan total DNS sistem akibat memasukkan hostname yang tidak melayani DoT.
     */
    suspend fun probe(hostname: String): DotProbeResult = withContext(Dispatchers.IO) {
        val validation = HostnameValidator.validate(hostname)
        if (validation is HostnameValidator.Result.Invalid) {
            return@withContext DotProbeResult.Failure(validation.reason)
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
                return@withContext DotProbeResult.Failure("Sesi TLS tidak valid")
            }

            DotProbeResult.Success
        } catch (e: CertificateException) {
            DotProbeResult.Failure("Sertifikat SSL/TLS server tidak valid atau kadaluarsa")
        } catch (e: SSLException) {
            DotProbeResult.Failure("Handshake TLS gagal pada port 853: ${e.message}")
        } catch (e: java.net.SocketTimeoutException) {
            DotProbeResult.Failure("Batas waktu terlampaui (Timeout): Server tidak merespons di port 853")
        } catch (e: java.net.UnknownHostException) {
            DotProbeResult.Failure("Hostname tidak dapat di-resolve (NXDOMAIN)")
        } catch (e: Exception) {
            DotProbeResult.Failure("Gagal terhubung ke port 853: ${e.javaClass.simpleName} - ${e.message}")
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
