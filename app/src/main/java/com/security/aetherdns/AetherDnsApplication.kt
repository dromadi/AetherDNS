package com.security.aetherdns

import android.app.Application
import com.security.aetherdns.adb.PairingNotifier
import com.security.aetherdns.adb.WirelessAdbActivator
import com.security.aetherdns.automation.AutomationSettings
import com.security.aetherdns.data.DnsRepository
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

    /** Hanya untuk merender notifikasi aktivasi dan widget mengikuti state; tidak ada kerja latar lain. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Paling awal agar crash saat inisialisasi pun tercatat untuk laporan diagnostik
        val crashRecorder = CrashRecorder(this).also { it.install() }
        diagnostics = DiagnosticsCollector(this, crashRecorder)
        // Sui (Shizuku via Magisk/KernelSU) hanya mengirim binder bila diinisialisasi; no-op tanpa Sui
        Sui.init(BuildConfig.APPLICATION_ID)
        dnsRepository = DnsRepository(this)
        permissionGranter = ShizukuGranter(this)
        profileRepository = ProfileRepository(SharedPrefsProfileStore(this))
        automationSettings = AutomationSettings(this)
        onboardingStore = OnboardingStore(this)
        wirelessActivator = WirelessAdbActivator(this)
        nextDnsStatsSettings = NextDnsStatsSettings(this)
        PairingNotifier(this).bind(appScope, wirelessActivator)
        // Widget layar utama mengikuti state DNS selama proses hidup (perubahan dari aplikasi, tile, Settings)
        appScope.launch { dnsRepository.state.collect { StatusWidget.updateAll(this@AetherDnsApplication, it) } }
    }
}
