package com.security.aetherdns.data

import com.security.aetherdns.util.DnsManager
import com.security.aetherdns.util.DotProbeCause
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.DotProber
import com.security.aetherdns.util.HostnameError
import com.security.aetherdns.util.HostnameValidator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class ApplyResult {
    /** [probe] berisi hasil uji DoT (latensi) bila probe dijalankan. */
    data class Success(val hostname: String, val probe: DotProbeResult.Success? = null) : ApplyResult()
    data class Failure(val error: ApplyError) : ApplyResult()
}

/** Penyebab kegagalan apply; teks untuk pengguna dipetakan di lapisan UI. */
sealed class ApplyError {
    data object NoPermission : ApplyError()
    data class InvalidHostname(val error: HostnameError) : ApplyError()
    data class ProbeFailed(val failure: DotProbeResult.Failure) : ApplyError()
    data object WriteRejected : ApplyError()
}

/**
 * Pipeline penerapan resolver DoT: validasi FQDN → probe DoT → (self-healing) → tulis `Settings.Global`.
 * Tidak bergantung pada Android framework sehingga seluruh jalurnya dapat diuji di JVM.
 */
class DnsApplier(
    private val settings: SecureSettings,
    private val prober: DotProber,
    private val retryDelayMs: Long = 1200L,
    private val retryAttempts: Int = 3
) {
    private val mutex = Mutex()

    suspend fun apply(hostname: String, probe: Boolean = true): ApplyResult = mutex.withLock {
        if (!settings.hasPermission()) {
            return ApplyResult.Failure(ApplyError.NoPermission)
        }

        val validation = HostnameValidator.validate(hostname)
        if (validation is HostnameValidator.Result.Invalid) {
            return ApplyResult.Failure(ApplyError.InvalidHostname(validation.error))
        }
        val targetFqdn = (validation as HostnameValidator.Result.Valid).fqdn

        if (!probe) {
            return commit(targetFqdn, null)
        }

        val firstProbe = prober.probe(targetFqdn)
        if (firstProbe is DotProbeResult.Success) {
            return commit(targetFqdn, firstProbe)
        }
        firstProbe as DotProbeResult.Failure

        // Self-healing HANYA bila resolusi gagal saat strict mode aktif (resolver lama mati sehingga DNS lumpuh).
        val originalMode = settings.mode()
        if (firstProbe.cause != DotProbeCause.RESOLVE || originalMode != DnsManager.MODE_HOSTNAME) {
            return ApplyResult.Failure(ApplyError.ProbeFailed(firstProbe))
        }
        val originalSpecifier = settings.specifier()

        var failure = ApplyResult.Failure(ApplyError.ProbeFailed(firstProbe))
        var committed: ApplyResult.Success? = null
        try {
            // Alihkan sementara ke opportunistic agar DNS dasar hidup dan targetFqdn dapat di-resolve
            settings.writeMode(DnsManager.MODE_OPPORTUNISTIC)

            // Retry bertahap: beri waktu ConnectivityService & cache negatif libcore untuk pulih
            for (attempt in 1..retryAttempts) {
                delay(retryDelayMs)
                when (val retry = prober.probe(targetFqdn)) {
                    is DotProbeResult.Success -> {
                        // Status "pulih" baru ditetapkan setelah konfigurasi baru benar-benar tertulis
                        when (val written = commit(targetFqdn, retry)) {
                            is ApplyResult.Success -> committed = written
                            is ApplyResult.Failure -> failure = written
                        }
                        break
                    }
                    is DotProbeResult.Failure -> failure = ApplyResult.Failure(ApplyError.ProbeFailed(retry))
                }
            }
        } finally {
            // Jika gagal, dibatalkan, atau penulisan akhir ditolak: pulihkan konfigurasi semula
            if (committed == null) {
                withContext(NonCancellable) {
                    restore(originalMode, originalSpecifier)
                }
            }
        }

        return committed ?: failure
    }

    private fun commit(fqdn: String, probe: DotProbeResult.Success?): ApplyResult {
        // Specifier ditulis lebih dulu agar mode "hostname" tidak pernah menunjuk ke specifier lama
        val ok = runCatching { settings.writeSpecifier(fqdn) && settings.writeMode(DnsManager.MODE_HOSTNAME) }
            .getOrDefault(false)
        if (!ok) return ApplyResult.Failure(ApplyError.WriteRejected)
        settings.onConfigChanged()
        return ApplyResult.Success(fqdn, probe)
    }

    private fun restore(mode: String, specifier: String) {
        runCatching {
            settings.writeSpecifier(specifier)
            settings.writeMode(mode)
        }
        settings.onConfigChanged()
    }
}
