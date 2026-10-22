package com.security.aetherdns

import android.app.Application
import com.security.aetherdns.autopilot.Autopilot
import com.security.aetherdns.autopilot.NetworkTriggers
import com.security.aetherdns.rules.SmartRules
import com.security.aetherdns.adb.PairingNotifier
import com.security.aetherdns.adb.WirelessAdbActivator
import com.security.aetherdns.automation.AutomationSettings
import com.security.aetherdns.data.DnsRepository
import com.security.aetherdns.data.FileInsightStore
import com.security.aetherdns.data.InsightStore
import com.security.aetherdns.diagnostics.CrashRecorder
import com.security.aetherdns.diagnostics.DiagnosticsCollector
import com.security.aetherdns.data.NextDnsStatsSettings
import com.security.aetherdns.data.ProfileRepository
import com.security.aetherdns.data.SharedPrefsProfileStore
import com.security.aetherdns.onboarding.OnboardingStore
import com.security.aetherdns.widget.StatusWidget
import com.security.aetherdns.shizuku.PermissionGranter
import com.security.aetherdns.shizuku.ShizukuGranter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.sui.Sui

class AetherDnsApplication : Application() {
    lateinit var dnsRepository: DnsRepository
        private set

    lateinit var permissionGranter: PermissionGranter
        private set

    lateinit var profileRepository: ProfileRepository
        private set

    lateinit var automationSettings: AutomationSettings
        private set

    lateinit var onboardingStore: OnboardingStore
        private set

    lateinit var wirelessActivator: WirelessAdbActivator
        private set

    lateinit var nextDnsStatsSettings: NextDnsStatsSettings
        private set

    lateinit var diagnostics: DiagnosticsCollector
        private set

    lateinit var insightStore: InsightStore
        private set

    lateinit var autopilot: Autopilot
        private set

    lateinit var smartRules: SmartRules
        private set

    /**
     * Satu urutan untuk semua pemicu (jaringan, alarm, aplikasi dibuka): Autopilot memastikan DNS bisa dipakai,
     * lalu aturan memilih resolver; bila aturan mengganti resolver, Autopilot memeriksa yang baru.
     */
    suspend fun evaluateNetwork(waitForNetwork: Boolean) {
        autopilot.evaluate(waitForNetwork)
        if (smartRules.evaluate()) autopilot.evaluate(waitForNetwork = false)
    }

    private fun syncNetworkTriggers() =
        NetworkTriggers.sync(this, autopilot.status.value.enabled || smartRules.enabled)

    /** Hanya untuk merender notifikasi aktivasi dan widget mengikuti state; tidak ada kerja latar lain. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Paling awal agar crash saat inisialisasi pun tercatat untuk laporan diagnostik
        val crashRecorder = CrashRecorder(this).also { it.install() }
        diagnostics = DiagnosticsCollector(this, crashRecorder)
        insightStore = FileInsightStore(noBackupFilesDir)
        // Sui (Shizuku via Magisk/KernelSU) hanya mengirim binder bila diinisialisasi; no-op tanpa Sui
        Sui.init(BuildConfig.APPLICATION_ID)
        dnsRepository = DnsRepository(this)
        permissionGranter = ShizukuGranter(this)
        profileRepository = ProfileRepository(SharedPrefsProfileStore(this))
        automationSettings = AutomationSettings(this)
        onboardingStore = OnboardingStore(this)
        wirelessActivator = WirelessAdbActivator(this)
        nextDnsStatsSettings = NextDnsStatsSettings(this)
        autopilot = Autopilot(this, dnsRepository, onEnabledChanged = ::syncNetworkTriggers)
        smartRules = SmartRules(this, dnsRepository, autopilotHolding = autopilot::isHolding, onEnabledChanged = ::syncNetworkTriggers)
        // Pendaftaran callback jaringan berbasis PendingIntent hilang saat reboot/update: daftarkan ulang setiap proses mulai
        syncNetworkTriggers()
        PairingNotifier(this).bind(appScope, wirelessActivator)
        // Widget layar utama mengikuti state DNS selama proses hidup (perubahan dari aplikasi, tile, Settings)
        appScope.launch { dnsRepository.state.collect { StatusWidget.updateAll(this@AetherDnsApplication, it) } }
    }
}
