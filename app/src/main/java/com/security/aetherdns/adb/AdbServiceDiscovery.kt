package com.security.aetherdns.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.NetworkInterface
import kotlin.coroutines.resume

/** Endpoint layanan ADB hasil mDNS. */
data class AdbEndpoint(val host: String, val port: Int)

/**
 * Menemukan layanan Wireless debugging milik perangkat ini lewat mDNS (NsdManager), seperti `adb mdns services`.
 * Layanan dari perangkat lain di Wi-Fi yang sama diabaikan dengan mencocokkan alamatnya ke antarmuka lokal.
 */
class AdbServiceDiscovery(context: Context) {

    private val nsd = context.getSystemService(NsdManager::class.java)

    suspend fun find(serviceType: String, timeoutMs: Long): AdbEndpoint? = withTimeoutOrNull(timeoutMs) {
        val found = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                found.trySend(info)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                found.close()
            }

            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        try {
            // Resolve satu per satu: di Android lama resolve paralel ditolak (FAILURE_ALREADY_ACTIVE)
            for (info in found) {
                val endpoint = resolve(info) ?: continue
                if (isLocalAddress(endpoint.host)) return@withTimeoutOrNull endpoint
            }
            null
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    @Suppress("DEPRECATION") // resolveService masih berfungsi di API 34+; pengganti baru hanya ada di API 34
    private suspend fun resolve(info: NsdServiceInfo): AdbEndpoint? = suspendCancellableCoroutine { cont ->
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress?.substringBefore('%')
                if (cont.isActive) cont.resume(host?.let { AdbEndpoint(it, serviceInfo.port) })
            }
        })
    }

    private fun isLocalAddress(host: String): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .any { it.hostAddress?.substringBefore('%') == host }
    }.getOrDefault(false)

    companion object {
        const val SERVICE_PAIRING = "_adb-tls-pairing._tcp"
        const val SERVICE_CONNECT = "_adb-tls-connect._tcp"
    }
}
