package com.security.aetherdns.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Aktivasi bawaan tanpa Shizuku (Android 11+): pairing ke Wireless debugging perangkat sendiri dengan kode
 * 6 digit, tersambung ke adbd lewat TLS, menjalankan `pm grant <paket> WRITE_SECURE_SETTINGS`, lalu memutus
 * koneksi. Tidak ada service atau proses yang tertinggal; keberhasilan ditentukan oleh status izin aktual.
 */
class WirelessAdbActivator(
    private val context: Context,
    private val discovery: AdbServiceDiscovery = AdbServiceDiscovery(context)
) {
    private val _state = MutableStateFlow<ActivationState>(ActivationState.Idle)
    val state: StateFlow<ActivationState> = _state.asStateFlow()

    private val mutex = Mutex()

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Pengguna memilih memasukkan kode lewat notifikasi; diabaikan bila aktivasi sedang berjalan. */
    fun awaitCode() {
        if (!_state.value.isWorking) _state.value = ActivationState.AwaitingCode
    }

    fun reset() {
        if (!_state.value.isWorking) _state.value = ActivationState.Idle
    }

    /** Menjalankan seluruh alur; panggilan ganda saat masih berjalan langsung mengembalikan state saat ini. */
    suspend fun activate(rawCode: String): ActivationState {
        if (!mutex.tryLock()) return _state.value
        try {
            val result = when {
                !isSupported -> ActivationState.Failed(ActivationError.Unsupported)
                PairingCode.normalize(rawCode) == null -> ActivationState.Failed(ActivationError.InvalidCode)
                else -> withTimeoutOrNull(TOTAL_TIMEOUT_MS) { run(PairingCode.normalize(rawCode)!!) }
                    ?: ActivationState.Failed(ActivationError.Timeout)
            }
            _state.value = result
            return result
        } catch (e: CancellationException) {
            _state.value = ActivationState.Idle
            throw e
        } catch (e: Exception) {
            return ActivationState.Failed(ActivationError.Unexpected("${e.javaClass.simpleName}: ${e.message}"))
                .also { _state.value = it }
        } finally {
            runCatching { AdbIdentity.instance.disconnect() }
            mutex.unlock()
        }
    }

    private suspend fun run(code: String): ActivationState {
        if (hasPermission()) return ActivationState.Success

        step(ActivationStep.FINDING_PAIRING)
        val pairing = discovery.find(AdbServiceDiscovery.SERVICE_PAIRING, DISCOVERY_TIMEOUT_MS)
            ?: return failed(ActivationError.PairingServiceNotFound)

        step(ActivationStep.PAIRING)
        val adb = AdbIdentity.instance
        // Alamat Wi-Fi hasil mDNS dulu, loopback sebagai cadangan (sebagian ROM hanya menerima salah satunya)
        var paired = false
        for (host in candidateHosts(pairing.host)) {
            try {
                runInterruptible(Dispatchers.IO) { adb.pair(host, pairing.port, code) }
                paired = true
                break
            } catch (e: CancellationException) {
                throw e // batas waktu total tidak boleh tertelan sebagai kegagalan pairing
            } catch (e: Exception) {
                if (!ActivationFailures.isUnreachable(e)) return failed(ActivationFailures.pairing(e))
            }
        }
        if (!paired) return failed(ActivationError.PairingUnreachable)

        step(ActivationStep.FINDING_CONNECT)
        val connect = discovery.find(AdbServiceDiscovery.SERVICE_CONNECT, DISCOVERY_TIMEOUT_MS)
            ?: return failed(ActivationError.ConnectServiceNotFound)

        step(ActivationStep.CONNECTING)
        var lastError: Exception? = null
        var connected = false
        for (host in candidateHosts(connect.host)) {
            try {
                connected = runInterruptible(Dispatchers.IO) {
                    adb.disconnect()
                    adb.connect(host, connect.port)
                }
                if (connected) break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (!connected) return failed(ActivationError.ConnectFailed(lastError?.let { "${it.javaClass.simpleName}: ${it.message}" }))

        step(ActivationStep.GRANTING)
        val output = runInterruptible(Dispatchers.IO) {
            adb.openStream("shell:pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS} 2>&1").use { stream ->
                stream.openInputStream().bufferedReader().readText()
            }
        }
        return ActivationFailures.grant(output, hasPermission())?.let { failed(it) } ?: ActivationState.Success
    }

    private fun candidateHosts(discovered: String): List<String> = listOf(discovered, LOOPBACK).distinct()

    private fun step(step: ActivationStep) {
        _state.value = ActivationState.Working(step)
    }

    private fun failed(error: ActivationError) = ActivationState.Failed(error)

    private fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val DISCOVERY_TIMEOUT_MS = 10_000L
        const val TOTAL_TIMEOUT_MS = 45_000L
    }
}
