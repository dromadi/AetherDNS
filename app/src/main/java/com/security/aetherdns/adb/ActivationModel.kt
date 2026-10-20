package com.security.aetherdns.adb

import com.security.aetherdns.shizuku.GrantError
import com.security.aetherdns.shizuku.GrantOutputParser
import java.io.IOException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/** Tahap aktivasi bawaan yang sedang berjalan (ditampilkan di aplikasi dan notifikasi). */
enum class ActivationStep {
    FINDING_PAIRING,
    PAIRING,
    FINDING_CONNECT,
    CONNECTING,
    GRANTING
}

/** Penyebab kegagalan aktivasi bawaan; teks untuk pengguna dipetakan di `ui/UiText.kt`. */
sealed class ActivationError {
    /** Wireless debugging butuh Android 11+. */
    data object Unsupported : ActivationError()
    data object InvalidCode : ActivationError()
    /** Layanan pairing tidak ditemukan: dialog "kode penyambungan" belum/tidak lagi terbuka. */
    data object PairingServiceNotFound : ActivationError()
    /** Layanan pairing ditemukan tetapi tidak bisa dihubungi (firewall, Auto Blocker, dialog tertutup). */
    data object PairingUnreachable : ActivationError()
    /** Kode salah atau kedaluwarsa. */
    data object PairingRejected : ActivationError()
    /** Pairing berhasil, tetapi layanan Wireless debugging (connect) tidak ditemukan. */
    data object ConnectServiceNotFound : ActivationError()
    data class ConnectFailed(val detail: String?) : ActivationError()
    data class Grant(val error: GrantError) : ActivationError()
    data object Timeout : ActivationError()
    data class Unexpected(val detail: String?) : ActivationError()
}

sealed class ActivationState {
    data object Idle : ActivationState()
    /** Notifikasi input kode sedang ditampilkan, menunggu pengguna. */
    data object AwaitingCode : ActivationState()
    data class Working(val step: ActivationStep) : ActivationState()
    data object Success : ActivationState()
    data class Failed(val error: ActivationError) : ActivationState()

    val isWorking: Boolean get() = this is Working
}

/** Kode penyambungan Wireless debugging: 6 digit; spasi/tanda hubung dari salin-tempel diabaikan. */
object PairingCode {
    const val LENGTH = 6

    fun normalize(input: String): String? {
        val digits = input.filterNot { it.isWhitespace() || it == '-' }
        return digits.takeIf { it.length == LENGTH && it.all { c -> c in '0'..'9' } }
    }
}

/** Logika murni pemetaan exception libadb ke [ActivationError] (diuji di `ActivationModelTest`). */
object ActivationFailures {
    /**
     * Pairing: gagal membuka socket berarti layanan tidak terjangkau; TLS dan SPAKE2 dimulai setelah socket
     * tersambung, sehingga kegagalan pertukaran pesan (`IOException` biasa) berarti kode salah / kedaluwarsa.
     */
    fun pairing(e: Throwable): ActivationError = when (e) {
        is SocketTimeoutException, is NoRouteToHostException -> ActivationError.PairingUnreachable
        is SocketException -> ActivationError.PairingUnreachable // termasuk ConnectException
        is SSLException -> ActivationError.Unexpected(e.message)
        is IOException -> ActivationError.PairingRejected
        else -> ActivationError.Unexpected("${e.javaClass.simpleName}: ${e.message}")
    }

    /** Hanya kegagalan terjangkau (socket) yang layak dicoba ulang ke alamat berikutnya. */
    fun isUnreachable(e: Throwable): Boolean = pairing(e) == ActivationError.PairingUnreachable

    /**
     * Keluaran `pm grant` lewat shell ADB (tanpa exit code). Sumber kebenaran tetap status izin aktual.
     */
    fun grant(output: String, permissionGranted: Boolean): ActivationError? {
        if (permissionGranted) return null
        val text = output.trim()
        return if (GrantOutputParser.isOemRestriction(text)) {
            ActivationError.Grant(GrantError.OemRestricted)
        } else {
            ActivationError.Grant(GrantError.PmFailed("?", text.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }))
        }
    }
}
