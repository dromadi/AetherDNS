package com.security.aetherdns

import com.security.aetherdns.data.PauseStore
import com.security.aetherdns.data.ResumeAlarm
import com.security.aetherdns.data.SecureSettings
import com.security.aetherdns.util.DnsManager

class FakeSecureSettings(
    var mode: String = DnsManager.MODE_OFF,
    var specifier: String = "",
    var permission: Boolean = true,
    var rejectSpecifier: String? = null,
    var rejectModes: Set<String> = emptySet()
) : SecureSettings {
    val modeHistory = mutableListOf<String>()

    override fun hasPermission() = permission
    override fun mode() = mode
    override fun specifier() = specifier

    override fun writeMode(mode: String): Boolean {
        if (mode in rejectModes) return false
        this.mode = mode
        modeHistory += mode
        return true
    }

    override fun writeSpecifier(specifier: String): Boolean {
        if (specifier == rejectSpecifier) return false
        this.specifier = specifier
        return true
    }
}

class FakePauseStore(var until: Long? = null) : PauseStore {
    override fun pausedUntil() = until
    override fun save(untilMs: Long) { until = untilMs }
    override fun clear() { until = null }
}

class FakeResumeAlarm : ResumeAlarm {
    var scheduledAt: Long? = null
    override fun schedule(atMs: Long) { scheduledAt = atMs }
    override fun cancel() { scheduledAt = null }
}
