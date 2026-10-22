package com.security.aetherdns.autopilot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.security.aetherdns.data.ApplyResult
import com.security.aetherdns.data.ChangeSource
import com.security.aetherdns.data.DnsRepository
import com.security.aetherdns.data.ProtectionSnapshot
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.util.NetworkInspector
import com.security.aetherdns.util.Reachability
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AutopilotStatus(val enabled: Boolean, val hold: AutopilotHold?)

/**
 * Menjaga proteksi tanpa service latar: sistem membangunkan [AutopilotReceiver] setiap jaringan berganti
 * ([NetworkTriggers]) dan, selama Autopilot sedang menahan proteksi,
 * lewat satu alarm inexact. Semua keputusan ada di [AutopilotPolicy]; kelas ini hanya mengumpulkan fakta
 * dan menjalankan keputusan lewat [DnsRepository] (sumber riwayat: `ChangeSource.AUTOPILOT`).
 */
class Autopilot(
    private val context: Context,
    private val repository: DnsRepository,
    private val store: AutopilotStore = AutopilotStore(context),
    private val inspector: NetworkInspector = NetworkInspector(),
    private val notifier: AutopilotNotifier = AutopilotNotifier(context),
    /** Dipanggil saat sakelar berubah agar pemicu jaringan bersama ([NetworkTriggers]) disinkronkan. */
    private val onEnabledChanged: () -> Unit = {}
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(AutopilotStatus(store.enabled, store.hold))
    val status: StateFlow<AutopilotStatus> = _status.asStateFlow()

    private val connectivity get() = context.getSystemService(ConnectivityManager::class.java)

    fun enable() {
        store.enabled = true
        onEnabledChanged()
        publish()
    }

    /** True bila Autopilot sedang menahan proteksi (dijeda / resolver pengganti); Aturan Pintar menunggu. */
    fun isHolding(): Boolean = store.enabled && AutopilotPolicy.validHold(store.hold, snapshot()) != null

    /** Mematikan Autopilot: proteksi yang sedang ditahan dikembalikan ke pilihan pengguna. */
    suspend fun disableAndRelease() = mutex.withLock {
        store.enabled = false
        cancelRecheck()
        onEnabledChanged()
        val hold = AutopilotPolicy.validHold(store.hold, snapshot())
        if (hold != null) repository.apply(hold.original, probe = false, source = ChangeSource.AUTOPILOT)
        clearHold()
        notifier.cancel()
        publish()
    }

    /**
     * @param waitForNetwork true saat dipicu jaringan baru: tunggu sebentar sampai Android selesai memvalidasi
     * (atau mendeteksi portal login) agar keputusan tidak diambil dari jaringan yang belum siap.
     */
    suspend fun evaluate(waitForNetwork: Boolean) = mutex.withLock {
        if (!store.enabled) return@withLock
        val state = repository.loadCurrentState()
        if (!state.hasPermission) return@withLock
        if (waitForNetwork) awaitNetworkSettled()

        val snapshot = ProtectionSnapshot.of(state)
        val stored = store.hold
        val hold = AutopilotPolicy.validHold(stored, snapshot)
        if (stored != null && hold == null) clearHold() // pengguna sudah turun tangan

        val preferred = hold?.original ?: snapshot.specifier
        val decision = AutopilotPolicy.decide(
            AutopilotInput(snapshot, state.pausedUntil != null, hold, isCaptivePortal()),
            alternatives = alternativesFor(preferred),
            reference = REFERENCE,
            reachable = ::reachableWithRetry
        )
        act(decision, preferred, hold)
        publish()
    }

    private suspend fun act(decision: AutopilotDecision, preferred: String, hold: AutopilotHold?) {
        val now = System.currentTimeMillis()
        when (decision) {
            AutopilotDecision.None -> if (hold != null) scheduleRecheck()
            is AutopilotDecision.Pause -> {
                if (!repository.disable(ChangeSource.AUTOPILOT)) return
                saveHold(AutopilotHold(preferred, decision.reason, snapshot(), now))
                notifier.paused(decision.reason, preferred)
            }
            is AutopilotDecision.SwitchTo -> {
                if (repository.apply(decision.resolver, probe = false, source = ChangeSource.AUTOPILOT) !is ApplyResult.Success) return
                saveHold(AutopilotHold(preferred, AutopilotReason.RESOLVER_DOWN, snapshot(), now, substitute = decision.resolver))
                notifier.switched(preferred, decision.resolver)
            }
            is AutopilotDecision.Restore -> {
                if (repository.apply(decision.resolver, probe = false, source = ChangeSource.AUTOPILOT) !is ApplyResult.Success) return
                clearHold()
                notifier.restored(decision.resolver)
            }
        }
    }

    private fun snapshot() = ProtectionSnapshot.of(repository.loadCurrentState())

    private fun saveHold(hold: AutopilotHold) {
        store.hold = hold
        scheduleRecheck()
    }

    private fun clearHold() {
        store.hold = null
        cancelRecheck()
    }

    private fun publish() {
        _status.value = AutopilotStatus(store.enabled, store.hold)
    }

    /** Kegagalan tunggal bisa kebetulan (paket hilang saat berpindah jaringan): ulangi sekali sebelum percaya. */
    private suspend fun reachableWithRetry(hostname: String): Reachability {
        val first = inspector.reachability(hostname)
        if (first != Reachability.FAIL) return first
        delay(RETRY_DELAY_MS)
        return inspector.reachability(hostname)
    }

    private fun alternativesFor(preferred: String): List<String> {
        val preset = DefaultProfiles.list.firstOrNull { it.hostname.equals(preferred, ignoreCase = true) } ?: return emptyList()
        return DefaultProfiles.list.filter { it.group == preset.group }.map { it.hostname }
    }

    private fun capabilities(): NetworkCapabilities? =
        runCatching { connectivity?.let { it.getNetworkCapabilities(it.activeNetwork) } }.getOrNull()

    private fun isCaptivePortal(): Boolean =
        capabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true

    private suspend fun awaitNetworkSettled() {
        val deadline = SystemClock.elapsedRealtime() + SETTLE_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val caps = capabilities()
            if (caps != null && (
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                    )
            ) return
            delay(SETTLE_POLL_MS)
        }
    }

    private fun scheduleRecheck() {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + RECHECK_MS,
            recheckIntent()
        )
    }

    private fun cancelRecheck() {
        context.getSystemService(AlarmManager::class.java)?.cancel(recheckIntent())
    }

    private fun recheckIntent(): PendingIntent {
        val intent = Intent(context, AutopilotReceiver::class.java).setAction(AutopilotReceiver.ACTION_RECHECK)
        return PendingIntent.getBroadcast(
            context, REQUEST_RECHECK, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /** Pembanding: bila resolver anycast besar ini pun gagal, jaringannya yang memblokir DNS terenkripsi. */
        const val REFERENCE = "1dot1dot1dot1.cloudflare-dns.com"
        private const val RECHECK_MS = 15 * 60_000L
        private const val SETTLE_TIMEOUT_MS = 8_000L
        private const val SETTLE_POLL_MS = 500L
        private const val RETRY_DELAY_MS = 1_500L
        private const val REQUEST_RECHECK = 42
    }
}
