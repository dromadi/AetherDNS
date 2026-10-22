package com.security.aetherdns.automation

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

data class AutomationState(val enabled: Boolean, val token: String)

/**
 * Sakelar integrasi otomasi. Sumber kebenaran status aktif adalah status komponen [AutomationReceiver]
 * (nonaktif di manifest), sehingga saat dimatikan tidak ada aplikasi lain yang bisa mengirim broadcast ke sana sama sekali.
 */
class AutomationSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("automation", Context.MODE_PRIVATE)
    private val component = ComponentName(context, AutomationReceiver::class.java)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AutomationState> = _state.asStateFlow()

    fun token(): String = prefs.getString(KEY_TOKEN, null).orEmpty()

    fun setEnabled(enabled: Boolean) {
        if (enabled && token().isEmpty()) prefs.edit().putString(KEY_TOKEN, newToken()).apply()
        context.packageManager.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        _state.value = load()
    }

    /** Membuat token baru; perintah otomasi yang memakai token lama langsung ditolak. */
    fun regenerateToken() {
        prefs.edit().putString(KEY_TOKEN, newToken()).apply()
        _state.value = load()
    }

    private fun load() = AutomationState(
        enabled = context.packageManager.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        token = token()
    )

    private companion object {
        const val KEY_TOKEN = "token"
        private val random = SecureRandom()

        fun newToken(): String = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    }
}
