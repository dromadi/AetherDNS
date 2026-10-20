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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DnsRepository(
    private val context: Context,
    settings: SecureSettings = AndroidSecureSettings(context),
    prober: DotProber = DotProbe
) {
    private val contentResolver = context.contentResolver
    private val applier = DnsApplier(settings, prober)

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
        applier.apply(hostname, probe).also { refresh() }
    }

    fun disable(): Boolean {
        val result = DnsManager.disablePrivateDns(context)
        refresh()
        return result.isSuccess
    }
}
