package com.security.aetherdns.data

import android.content.Context
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.security.aetherdns.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class ApplyResult {
    data class Success(val hostname: String) : ApplyResult()
    data class Failure(val reason: String) : ApplyResult()
}

class DnsRepository(private val context: Context) {
    private val contentResolver = context.contentResolver
    private val applyMutex = Mutex()

    private val _state = MutableStateFlow(loadCurrentState())
    val state: StateFlow<DnsState> = _state.asStateFlow()

    private var contentObserver: ContentObserver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        startObserving()
        startNetworkMonitoring()
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

    private fun startNetworkMonitoring() {
        if (networkCallback != null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                refresh()
            }
        }

        try {
            cm.registerDefaultNetworkCallback(networkCallback!!)
        } catch (_: Exception) {}
    }

    suspend fun apply(hostname: String, probe: Boolean = true): ApplyResult = withContext(Dispatchers.IO) {
        applyMutex.withLock {
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

                // Self-healing: HANYA jika kegagalan disebabkan oleh kegagalan resolusi (RESOLVE / UnknownHostException)
                // dan mode sistem saat ini adalah hostname (strict mode lama rusak/mati sehingga DNS lumpuh)
                val currentMode = DnsManager.getPrivateDnsMode(context)
                if (probeResult is DotProbeResult.Failure &&
                    probeResult.cause == DotProbeCause.RESOLVE &&
                    currentMode == DnsManager.MODE_HOSTNAME
                ) {
                    val originalMode = currentMode
                    val originalSpecifier = DnsManager.getPrivateDnsSpecifier(context)
                    var healed = false

                    try {
                        // Alihkan sementara ke mode opportunistic agar DNS dasar bisa hidup dan me-resolve targetFqdn
                        Settings.Global.putString(contentResolver, DnsManager.SETTING_PRIVATE_DNS_MODE, DnsManager.MODE_OPPORTUNISTIC)

                        // Retry dengan delay bertahap untuk memberikan waktu ConnectivityService dan cache DNS libcore pulih
                        for (attempt in 1..3) {
                            delay(1200L)
                            val retryProbe = DotProbe.probe(targetFqdn)
                            if (retryProbe is DotProbeResult.Success) {
                                probeResult = DotProbeResult.Success
                                healed = true
                                break
                            }
                        }
                    } finally {
                        // Pastikan jika self-healing gagal, sistem tidak tertinggal di mode opportunistic
                        if (!healed) {
                            withContext(NonCancellable) {
                                try {
                                    Settings.Global.putString(contentResolver, DnsManager.SETTING_PRIVATE_DNS_MODE, originalMode)
                                    Settings.Global.putString(contentResolver, DnsManager.SETTING_PRIVATE_DNS_SPECIFIER, originalSpecifier)
                                } catch (_: Exception) {}
                            }
                        }
                    }
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
    }

    fun disable(): Boolean {
        val result = DnsManager.disablePrivateDns(context)
        refresh()
        return result.isSuccess
    }
}
