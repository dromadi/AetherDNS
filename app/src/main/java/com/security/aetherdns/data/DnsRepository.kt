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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DnsRepository(
    private val context: Context,
    settings: SecureSettings = AndroidSecureSettings(context),
    prober: DotProber = DotProbe,
    pauseStore: PauseStore = SharedPrefsPauseStore(context),
    resumeAlarm: ResumeAlarm = AlarmManagerResumeAlarm(context)
) {
    private val contentResolver = context.contentResolver
    private val applier = DnsApplier(settings, prober)
    private val pauseController = PauseController(settings, applier, pauseStore, resumeAlarm)

    private val _state = MutableStateFlow(loadCurrentState())
    val state: StateFlow<DnsState> = _state.asStateFlow()

    private val _networkChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Berbunyi setiap jaringan default berganti (mis. pindah Wi-Fi / ke data seluler), selama proses hidup. */
    val networkChanges: SharedFlow<Unit> = _networkChanges.asSharedFlow()

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
            validatedServerName = serverName,
            pausedUntil = pauseController.pausedUntil()
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
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                _networkChanges.tryEmit(Unit)
            }

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
        applier.apply(hostname, probe).also { result ->
            // Pilihan manual pengguna menggantikan jeda yang sedang berjalan
            if (result is ApplyResult.Success) pauseController.cancel()
            refresh()
        }
    }

    fun disable(): Boolean {
        pauseController.cancel()
        val result = DnsManager.disablePrivateDns(context)
        refresh()
        return result.isSuccess
    }

    /**
     * Mengaktifkan proteksi tanpa UI (Quick Settings Tile): melanjutkan jeda bila ada, jika tidak memakai
     * specifier tersimpan, dengan fallback ke resolver default bila specifier tidak valid.
     * Tanpa probe agar respons tile instan; specifier tersimpan sudah lolos probe saat pertama diterapkan.
     */
    suspend fun enableQuick(): ApplyResult = withContext(Dispatchers.IO) {
        if (pauseController.pausedUntil() != null) {
            return@withContext pauseController.resumeNow().also { refresh() }
        }
        val stored = DnsManager.getPrivateDnsSpecifier(context).trim()
        var result = apply(stored.ifEmpty { DnsManager.DEFAULT_FALLBACK_HOSTNAME }, probe = false)
        if (result is ApplyResult.Failure && stored.isNotEmpty()) {
            result = apply(DnsManager.DEFAULT_FALLBACK_HOSTNAME, probe = false)
        }
        result
    }

    suspend fun pause(durationMs: Long): PauseResult = withContext(Dispatchers.IO) {
        pauseController.pause(durationMs).also { refresh() }
    }

    suspend fun resumeNow(): ApplyResult = withContext(Dispatchers.IO) {
        pauseController.resumeNow().also { refresh() }
    }

    /** Dipanggil oleh alarm jeda dan setelah boot. */
    suspend fun resumeIfDue() = withContext(Dispatchers.IO) {
        pauseController.onAlarmOrBoot()
        refresh()
    }
}
