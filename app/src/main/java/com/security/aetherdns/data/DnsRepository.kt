package com.security.aetherdns.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.security.aetherdns.util.DnsManager
import com.security.aetherdns.util.DotProbe
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.HostnameValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed class ApplyResult {
    data class Success(val hostname: String) : ApplyResult()
    data class Failure(val reason: String) : ApplyResult()
}

class DnsRepository(private val context: Context) {
    private val contentResolver = context.contentResolver
    private val _state = MutableStateFlow(loadCurrentState())
    val state: StateFlow<DnsState> = _state.asStateFlow()

    private var contentObserver: ContentObserver? = null

    init {
        startObserving()
    }

    fun loadCurrentState(): DnsState {
        val hasPerm = DnsManager.hasSecurePermission(context)
        val mode = DnsManager.getPrivateDnsMode(context)
        val specifier = DnsManager.getPrivateDnsSpecifier(context)
        val isProtected = DnsManager.isProtectionActive(context)
        val (isDoTValidated, serverName) = DnsManager.getSystemLinkPropertiesStatus(context)

        return DnsState(
            hasPermission = hasPerm,
            mode = mode,
            specifier = specifier,
            isProtectionActive = isProtected,
            isSystemDoTValidated = isDoTValidated,
            validatedServerName = serverName
        )
    }

    fun refresh() {
        _state.value = loadCurrentState()
    }

    private fun startObserving() {
        if (contentObserver != null) return

        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                refresh()
            }
        }

        try {
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(DnsManager.SETTING_PRIVATE_DNS_MODE),
                false,
                contentObserver!!
            )
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(DnsManager.SETTING_PRIVATE_DNS_SPECIFIER),
                false,
                contentObserver!!
            )
        } catch (_: Exception) {}
    }

    suspend fun apply(hostname: String, probe: Boolean = true): ApplyResult = withContext(Dispatchers.IO) {
        if (!DnsManager.hasSecurePermission(context)) {
            return@withContext ApplyResult.Failure("Izin WRITE_SECURE_SETTINGS belum diberikan.")
        }

        val validation = HostnameValidator.validate(hostname)
        if (validation is HostnameValidator.Result.Invalid) {
            return@withContext ApplyResult.Failure(validation.reason)
        }
        val targetFqdn = (validation as HostnameValidator.Result.Valid).fqdn

        if (probe) {
            var probeResult = DotProbe.probe(targetFqdn)

            // Self-healing: Jika probe gagal karena resolver lama yang aktif sedang mati/rusak (NXDOMAIN / host unreachable)
            if (probeResult is DotProbeResult.Failure && DnsManager.getPrivateDnsMode(context) == DnsManager.MODE_HOSTNAME) {
                // Alihkan sementara ke mode opportunistic agar DNS dasar bisa berfungsi untuk me-resolve targetFqdn
                val originalSpecifier = DnsManager.getPrivateDnsSpecifier(context)
                try {
                    Settings.Global.putString(contentResolver, DnsManager.SETTING_PRIVATE_DNS_MODE, DnsManager.MODE_OPPORTUNISTIC)
                    // Coba probe ulang
                    val retryProbe = DotProbe.probe(targetFqdn)
                    if (retryProbe is DotProbeResult.Success) {
                        probeResult = DotProbeResult.Success
                    } else {
                        // Kembalikan ke konfigurasi awal jika tetap gagal
                        Settings.Global.putString(contentResolver, DnsManager.SETTING_PRIVATE_DNS_MODE, DnsManager.MODE_HOSTNAME)
                        Settings.Global.putString(contentResolver, DnsManager.SETTING_PRIVATE_DNS_SPECIFIER, originalSpecifier)
                    }
                } catch (_: Exception) {}
            }

            if (probeResult is DotProbeResult.Failure) {
                return@withContext ApplyResult.Failure("Uji DoT gagal: ${probeResult.reason}")
            }
        }

        val result = DnsManager.applyPrivateDns(context, targetFqdn)
        if (result.isSuccess) {
            refresh()
            ApplyResult.Success(targetFqdn)
        } else {
            ApplyResult.Failure(result.exceptionOrNull()?.message ?: "Gagal memperbarui pengaturan sistem")
        }
    }

    fun disable(): Boolean {
        val result = DnsManager.disablePrivateDns(context)
        refresh()
        return result.isSuccess
    }
}
