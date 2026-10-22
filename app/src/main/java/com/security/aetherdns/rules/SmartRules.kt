package com.security.aetherdns.rules

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit
import com.security.aetherdns.autopilot.AutopilotReceiver
import com.security.aetherdns.data.ApplyResult
import com.security.aetherdns.data.ChangeSource
import com.security.aetherdns.data.DnsRepository
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.model.PresetGroup
import com.security.aetherdns.util.DotProbe
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.DotProber
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneId

/** Aturan yang sedang diterapkan (untuk kartu status / halaman Aturan). */
data class AppliedRule(val hostname: String, val source: RuleSource)

data class RulesStatus(val rules: RuleSet, val base: String?, val applied: AppliedRule?)

/** Aturan + resolver pilihan utama. Ikut backup (pengaturan pengguna), seperti profil tersimpan. */
class RulesStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var rules: RuleSet
        get() = RulesCodec.decode(prefs.getString(KEY_RULES, null))
        set(value) = prefs.edit { putString(KEY_RULES, RulesCodec.encode(value)) }

    /** Resolver yang terakhir dipilih pengguna sendiri; dipakai bila tidak ada aturan yang berlaku. */
    var base: String?
        get() = prefs.getString(KEY_BASE, null)
        set(value) = prefs.edit { putString(KEY_BASE, value) }

    /** Konteks aturan saat pengguna memilih sendiri; aturan itu tidak menimpa pilihannya sampai konteks berganti. */
    var override: String?
        get() = prefs.getString(KEY_OVERRIDE, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_OVERRIDE) else putString(KEY_OVERRIDE, value) }

    private companion object {
        const val PREFS = "rules"
        const val KEY_RULES = "rules"
        const val KEY_BASE = "base"
        const val KEY_OVERRIDE = "override"
    }
}

/**
 * Aturan Pintar: resolver mengikuti jenis jaringan dan jadwal, tanpa service latar. Dipicu oleh pergantian
 * jaringan (bersama Autopilot, lewat [AutopilotReceiver]), satu alarm inexact di batas jadwal berikutnya,
 * dan saat aplikasi dibuka. Keputusan murni di [RuleEngine].
 */
class SmartRules(
    private val context: Context,
    private val repository: DnsRepository,
    private val store: RulesStore = RulesStore(context),
    private val prober: DotProber = DotProbe,
    /** Autopilot sedang menahan proteksi: aturan menunggu agar keduanya tidak saling melawan. */
    private val autopilotHolding: () -> Boolean = { false },
    private val onEnabledChanged: () -> Unit = {}
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(RulesStatus(store.rules, store.base, null))
    val status: StateFlow<RulesStatus> = _status.asStateFlow()

    init {
        // Pilihan manual (aplikasi / otomasi) menjadi pilihan utama; perubahan oleh aturan atau Autopilot tidak
        repository.onChange = { state, source ->
            if ((source == ChangeSource.APP || source == ChangeSource.AUTOMATION) && state.isProtectionActive) {
                store.base = state.specifier
                store.override = RuleEngine.decide(store.rules, networkType(), now())?.let { contextKey(it.source) }
                publish(applied = null)
            }
        }
    }

    val enabled: Boolean get() = store.rules.enabled

    /** Menyimpan aturan; mematikan aturan mengembalikan pilihan utama bila aturan sedang menggantinya. */
    suspend fun update(rules: RuleSet) = mutex.withLock {
        val wasEnabled = store.rules.enabled
        store.rules = rules
        if (wasEnabled != rules.enabled) onEnabledChanged()
        if (!rules.enabled) {
            cancelAlarm()
            val state = repository.loadCurrentState()
            val base = store.base
            val applied = _status.value.applied
            if (applied != null && base != null && state.isProtectionActive && state.specifier.equals(applied.hostname, ignoreCase = true)) {
                repository.apply(base, probe = false, source = ChangeSource.RULES)
            }
            publish(applied = null)
        } else {
            publish(applied = _status.value.applied)
        }
    }

    /** @return true bila resolver diganti. */
    suspend fun evaluate(): Boolean = mutex.withLock {
        val rules = store.rules
        if (!rules.enabled) return@withLock false
        scheduleAlarm(rules)
        val state = repository.loadCurrentState()
        if (!state.hasPermission || !state.isProtectionActive || state.pausedUntil != null || autopilotHolding()) {
            return@withLock false
        }
        val base = store.base ?: state.specifier.also { store.base = it }
        val decision = RuleEngine.decide(rules, networkType(), now())
        val context = decision?.let { contextKey(it.source) }
        if (context != null && context == store.override) {
            publish(applied = null)
            return@withLock false
        }
        store.override = null
        val desired = when (val target = decision?.target) {
            null -> base
            is RuleTarget.Resolver -> target.hostname
            RuleTarget.Fastest -> fastest(base) ?: state.specifier
        }
        val changed = !desired.equals(state.specifier, ignoreCase = true) &&
            repository.apply(desired, probe = false, source = ChangeSource.RULES) is ApplyResult.Success
        publish(applied = decision?.let { AppliedRule(desired, it.source) })
        changed
    }

    /** Resolver tercepat dari kelompok pilihan utama (atau pemblokir iklan bila pilihan utama bukan preset). */
    private suspend fun fastest(base: String): String? = coroutineScope {
        val group = DefaultProfiles.list.firstOrNull { it.hostname.equals(base, ignoreCase = true) }?.group ?: PresetGroup.ADS
        DefaultProfiles.list.filter { it.group == group }
            .map { profile -> async { profile.hostname to prober.probe(profile.hostname) } }
            .awaitAll()
            .mapNotNull { (host, result) -> (result as? DotProbeResult.Success)?.let { host to it.totalMs } }
            .minByOrNull { it.second }?.first
    }

    fun currentNetworkType(): NetworkType = networkType()

    private fun contextKey(source: RuleSource): String = when (source) {
        is RuleSource.Schedule -> "S:${source.rule.id}"
        is RuleSource.Network -> "N:${source.type}"
    }

    private fun networkType(): NetworkType {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return NetworkType.OTHER
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.OTHER
        }
    }

    private fun now(): LocalDateTime = LocalDateTime.now()

    private fun publish(applied: AppliedRule?) {
        _status.value = RulesStatus(store.rules, store.base, applied)
    }

    private fun scheduleAlarm(rules: RuleSet) {
        val next = RuleEngine.nextBoundary(rules, now()) ?: return cancelAlarm()
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent())
    }

    private fun cancelAlarm() {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent())
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, AutopilotReceiver::class.java).setAction(AutopilotReceiver.ACTION_RULES),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val REQUEST_ALARM = 43
    }
}
