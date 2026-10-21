package com.security.aetherdns.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.security.aetherdns.R
import com.security.aetherdns.adb.ActivationState
import com.security.aetherdns.adb.WirelessAdbActivator
import com.security.aetherdns.automation.AutomationSettings
import com.security.aetherdns.automation.AutomationState
import com.security.aetherdns.data.ApplyResult
import com.security.aetherdns.data.DnsRepository
import com.security.aetherdns.data.DnsState
import com.security.aetherdns.data.InsightSample
import com.security.aetherdns.data.InsightStore
import com.security.aetherdns.data.NetworkKey
import com.security.aetherdns.diagnostics.DiagnosticReportFormatter
import com.security.aetherdns.diagnostics.DiagnosticSnapshot
import com.security.aetherdns.diagnostics.DiagnosticsCollector
import com.security.aetherdns.diagnostics.FeatureStatus
import com.security.aetherdns.data.NextDnsClient
import com.security.aetherdns.data.NextDnsStatsSettings
import com.security.aetherdns.data.NextDnsSummary
import com.security.aetherdns.data.StatsError
import com.security.aetherdns.data.HistoryEvent
import com.security.aetherdns.data.NextDnsWeek
import com.security.aetherdns.data.WeekResult
import com.security.aetherdns.data.StatsResult
import com.security.aetherdns.data.PauseResult
import com.security.aetherdns.data.ProfileRepository
import com.security.aetherdns.data.ProfileSaveResult
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.model.DnsProfile
import com.security.aetherdns.onboarding.OnboardingStore
import com.security.aetherdns.shizuku.GranterStatus
import com.security.aetherdns.shizuku.PermissionGranter
import com.security.aetherdns.util.AuditSummary
import com.security.aetherdns.util.DnsAuditor
import com.security.aetherdns.util.DotProbe
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.InspectionResult
import com.security.aetherdns.util.StabilityAdvice
import com.security.aetherdns.util.StabilityResult
import com.security.aetherdns.util.StabilityTest
import com.security.aetherdns.util.CheckResult
import com.security.aetherdns.util.DnsManager
import com.security.aetherdns.util.NetworkCheck
import com.security.aetherdns.util.NetworkInspector
import com.security.aetherdns.util.NextDnsHostname
import com.security.aetherdns.util.NetworkReport
import com.security.aetherdns.util.ResolverInspector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

sealed class UiEvent {
    data class ShowSnackbar(val message: UiText) : UiEvent()

    /** Cek otomatis menemukan DNS terenkripsi diblokir saat proteksi aktif: tawarkan jeda. */
    data object DotBlockedOnNetwork : UiEvent()
}

/** State kartu laporan diagnostik. */
sealed class ReportUiState {
    data object Idle : ReportUiState()
    data object Loading : ReportUiState()
    data class Ready(val text: String, val hasCrash: Boolean) : ReportUiState()
}

/** State kartu statistik blokir NextDNS. */
sealed class StatsUiState {
    data object Idle : StatsUiState()
    data object Loading : StatsUiState()
    data class Loaded(val summary: NextDnsSummary) : StatsUiState()
    data class Error(val error: StatsError) : StatsUiState()
}

sealed class WeekUiState {
    data object Idle : WeekUiState()
    data object Loading : WeekUiState()
    data class Loaded(val week: NextDnsWeek) : WeekUiState()
    data class Error(val error: StatsError) : WeekUiState()
}

/** Uji stabilitas koneksi DoT (Insight): resolver aktif, resolver sekelompok, dan pembanding. */
sealed class StabilityUiState {
    data object Idle : StabilityUiState()
    data class Running(val round: Int, val total: Int) : StabilityUiState()
    data class Done(val active: String, val results: List<StabilityResult>, val advice: StabilityAdvice) : StabilityUiState()
}

/** Hasil "Uji Kecepatan & Filter" per ID profil. */
data class BenchmarkState(
    val isRunning: Boolean = false,
    val results: Map<String, InspectionResult> = emptyMap()
) {
    /** ID profil dengan total latensi terendah di antara pemeriksaan yang berhasil. */
    val fastestId: String?
        get() = results.entries
            .mapNotNull { (id, r) -> (r as? InspectionResult.Success)?.let { id to it.latency.totalMs } }
            .minByOrNull { it.second }?.first
}

class MainViewModel(
    private val repository: DnsRepository,
    private val granter: PermissionGranter,
    private val profileRepository: ProfileRepository,
    private val automationSettings: AutomationSettings,
    private val onboardingStore: OnboardingStore,
    private val activator: WirelessAdbActivator,
    private val statsSettings: NextDnsStatsSettings,
    private val diagnostics: DiagnosticsCollector,
    private val insightStore: InsightStore,
    private val statsClient: NextDnsClient = NextDnsClient(),
    private val inspector: ResolverInspector = DotProbe,
    private val networkInspector: NetworkInspector = NetworkInspector(),
    private val auditor: DnsAuditor = DnsAuditor()
) : ViewModel() {

    val automation: StateFlow<AutomationState> = automationSettings.state

    private val _showOnboarding = MutableStateFlow(
        repository.state.value.let { onboardingStore.shouldShow(it.hasPermission, it.isProtectionActive) }
    )
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    val dnsState: StateFlow<DnsState> = repository.state
    val savedProfiles: StateFlow<List<DnsProfile>> = profileRepository.profiles

    private val _benchmark = MutableStateFlow(BenchmarkState())
    val benchmark: StateFlow<BenchmarkState> = _benchmark.asStateFlow()

    private val _shizukuStatus = MutableStateFlow(granter.status())
    val shizukuStatus: StateFlow<GranterStatus> = _shizukuStatus.asStateFlow()

    private val _isProbing = MutableStateFlow(false)
    val isProbing: StateFlow<Boolean> = _isProbing.asStateFlow()

    private val _isGranting = MutableStateFlow(false)
    val isGranting: StateFlow<Boolean> = _isGranting.asStateFlow()

    private val _isAuditing = MutableStateFlow(false)
    val isAuditing: StateFlow<Boolean> = _isAuditing.asStateFlow()

    private val _auditSummary = MutableStateFlow<AuditSummary?>(null)
    val auditSummary: StateFlow<AuditSummary?> = _auditSummary.asStateFlow()

    /** Aktivasi bawaan (Wireless debugging tanpa Shizuku), Android 11+. */
    val activationState: StateFlow<ActivationState> = activator.state
    val isNativeActivationSupported: Boolean get() = activator.isSupported

    private val _networkReport = MutableStateFlow<NetworkReport?>(null)
    val networkReport: StateFlow<NetworkReport?> = _networkReport.asStateFlow()

    private val _isCheckingNetwork = MutableStateFlow(false)
    val isCheckingNetwork: StateFlow<Boolean> = _isCheckingNetwork.asStateFlow()

    /** Statistik blokir NextDNS; hanya relevan bila resolver aktif adalah profil NextDNS pribadi. */
    val statsApiKey: StateFlow<String?> = statsSettings.apiKey

    private val _stats = MutableStateFlow<StatsUiState>(StatsUiState.Idle)
    val stats: StateFlow<StatsUiState> = _stats.asStateFlow()
    private var lastStatsAt = 0L

    /** Config ID NextDNS dari resolver yang sedang aktif, atau null bila bukan profil NextDNS pribadi. */
    private fun activeNextDnsProfile(): String? =
        dnsState.value.takeIf { it.isProtectionActive }?.specifier?.let { NextDnsHostname.extractConfigId(it) }

    fun refreshStats() {
        val profile = activeNextDnsProfile() ?: return
        val key = statsSettings.apiKey.value ?: return
        if (_stats.value is StatsUiState.Loading) return
        viewModelScope.launch {
            _stats.value = StatsUiState.Loading
            _stats.value = when (val result = statsClient.summary(profile, key)) {
                is StatsResult.Success -> StatsUiState.Loaded(result.summary)
                is StatsResult.Failure -> StatsUiState.Error(result.error)
            }
            lastStatsAt = System.currentTimeMillis()
        }
    }

    private val _statsWeek = MutableStateFlow<WeekUiState>(WeekUiState.Idle)
    val statsWeek: StateFlow<WeekUiState> = _statsWeek.asStateFlow()

    /** Statistik NextDNS 7 hari untuk halaman Insight; dimuat saat halaman dibuka. */
    fun refreshWeekStats() {
        val profile = activeNextDnsProfile() ?: return
        val key = statsSettings.apiKey.value ?: return
        if (_statsWeek.value is WeekUiState.Loading) return
        viewModelScope.launch {
            _statsWeek.value = WeekUiState.Loading
            _statsWeek.value = when (val result = statsClient.week(profile, key, TimeZone.getDefault().id)) {
                is WeekResult.Success -> WeekUiState.Loaded(result.week)
                is WeekResult.Failure -> WeekUiState.Error(result.error)
            }
        }
    }

    fun saveStatsApiKey(key: String) {
        statsSettings.setApiKey(key)
        refreshStats()
    }

    fun clearStatsApiKey() {
        statsSettings.setApiKey(null)
        _stats.value = StatsUiState.Idle
        _statsWeek.value = WeekUiState.Idle
    }

    /** Riwayat lokal untuk halaman Insight (kecepatan resolver & rapor jaringan); tidak pernah dikirim ke mana pun. */
    private val _insights = MutableStateFlow<List<InsightSample>>(emptyList())
    val insights: StateFlow<List<InsightSample>> = _insights.asStateFlow()

    private val _currentNetwork = MutableStateFlow(NetworkKey("none", ""))
    val currentNetwork: StateFlow<NetworkKey> = _currentNetwork.asStateFlow()

    private fun networkKey(): NetworkKey = diagnostics.network().let { NetworkKey(it.transport, it.carrier) }.also {
        _currentNetwork.value = it
    }

    private suspend fun recordInsight(sample: InsightSample) {
        _insights.value = withContext(Dispatchers.IO) {
            insightStore.append(sample)
            insightStore.all()
        }
    }

    /** Mengukur kecepatan resolver aktif sekali dan mencatatnya (dipanggil saat aplikasi tampil / periksa ulang). */
    private fun sampleLatency() {
        val state = dnsState.value
        if (!state.isProtectionActive || state.specifier.isBlank()) return
        viewModelScope.launch {
            val network = networkKey()
            val result = DotProbe.probe(state.specifier)
            recordInsight(
                InsightSample.Latency(
                    System.currentTimeMillis(), network, state.specifier,
                    (result as? DotProbeResult.Success)?.totalMs
                )
            )
        }
    }

    private val _stability = MutableStateFlow<StabilityUiState>(StabilityUiState.Idle)
    val stability: StateFlow<StabilityUiState> = _stability.asStateFlow()

    /**
     * Mengukur resolver aktif berulang kali bersama resolver sekelompok dan Cloudflare sebagai pembanding,
     * untuk menjawab "kenapa internet putus-nyambung": resolver-nya, atau jaringannya.
     */
    fun runStabilityTest() {
        if (_stability.value is StabilityUiState.Running) return
        val active = dnsState.value.specifier.trim().ifEmpty { DnsManager.DEFAULT_FALLBACK_HOSTNAME }
        val preset = DefaultProfiles.list.firstOrNull { it.hostname.equals(active, ignoreCase = true) }
        val alternatives = preset?.let { p -> DefaultProfiles.list.filter { it.group == p.group }.map { it.hostname } }.orEmpty()
        val hosts = (listOf(active) + alternatives + STABILITY_REFERENCE).distinctBy { it.lowercase() }
        viewModelScope.launch {
            val network = networkKey()
            _stability.value = StabilityUiState.Running(0, StabilityTest.ROUNDS)
            val results = StabilityTest(DotProbe).run(hosts) { round ->
                _stability.value = StabilityUiState.Running(round, StabilityTest.ROUNDS)
            }
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                results.forEach {
                    val isActive = it.resolver.equals(active, ignoreCase = true)
                    insightStore.append(InsightSample.Latency(now, network, it.resolver, it.medianMs, benchmark = !isActive))
                }
            }
            _insights.value = withContext(Dispatchers.IO) { insightStore.all() }
            _stability.value = StabilityUiState.Done(
                active,
                results,
                StabilityAdvice.of(results, active, alternatives.toSet(), STABILITY_REFERENCE)
            )
        }
    }

    /** Riwayat aktif / jeda / matikan / ganti resolver (dari aplikasi, tile, pintasan, otomasi, atau Settings HP). */
    val history: StateFlow<List<HistoryEvent>> = repository.history

    fun clearInsights() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                insightStore.clear()
                repository.clearHistory()
            }
            _insights.value = emptyList()
        }
    }

    private val _report = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val report: StateFlow<ReportUiState> = _report.asStateFlow()

    /**
     * Laporan diagnostik semua fitur: menjalankan probe resolver aktif, cek jaringan, dan audit sekarang juga,
     * lalu menyusun teks yang bisa disalin. Tidak memuat token otomasi, API key, SSID, atau IP perangkat.
     */
    fun generateReport() {
        if (_report.value is ReportUiState.Loading) return
        viewModelScope.launch {
            _report.value = ReportUiState.Loading
            val state = dnsState.value
            val activeHost = state.specifier.takeIf { state.isProtectionActive && it.isNotBlank() }
            val probe = activeHost?.let { host -> async { DotProbe.probe(host) } }
            val network = async { networkInspector.inspect(activeHost ?: DnsManager.DEFAULT_FALLBACK_HOSTNAME) }
            val direct = activeHost?.let { host -> async { inspector.inspect(host) } }
            val audit = auditor.runAudit().reconcileWith(
                (direct?.await() as? InspectionResult.Success)?.filter?.blocked?.toSet().orEmpty()
            )
            val networkReport = network.await().also { _networkReport.value = it }
            _auditSummary.value = audit

            val preset = (DefaultProfiles.list + profileRepository.profiles.value)
                .firstOrNull { it.hostname.equals(state.specifier, ignoreCase = true) }
            val crash = diagnostics.crashRecorder.lastCrash()
            val snapshot = DiagnosticSnapshot(
                generatedAt = System.currentTimeMillis(),
                device = diagnostics.device(),
                network = diagnostics.network(),
                features = FeatureStatus(
                    hasPermission = state.hasPermission,
                    mode = state.mode,
                    specifier = state.specifier,
                    presetName = preset?.let { if (it.isCustom) "saved profile" else it.id },
                    protectionActive = state.isProtectionActive,
                    pausedUntil = state.pausedUntil,
                    nativeActivationSupported = activator.isSupported,
                    activationState = activator.state.value.toString(),
                    shizukuStatus = granter.status().name,
                    notificationsAllowed = diagnostics.notificationsAllowed(),
                    automationEnabled = automationSettings.state.value.enabled,
                    nextDnsApiKeySet = statsSettings.apiKey.value != null,
                    nextDnsProfileDetected = NextDnsHostname.extractConfigId(state.specifier) != null,
                    savedProfiles = profileRepository.profiles.value.size,
                    widgetsPlaced = diagnostics.widgetsPlaced(),
                    onboardingCompleted = onboardingStore.isCompleted()
                ),
                activeResolverProbe = probe?.await(),
                networkReport = networkReport,
                audit = audit,
                lastCrash = crash
            )
            _report.value = ReportUiState.Ready(DiagnosticReportFormatter.format(snapshot), hasCrash = crash != null)
        }
    }

    fun clearCrashLog() {
        diagnostics.crashRecorder.clear()
        (_report.value as? ReportUiState.Ready)?.let { _report.value = it.copy(hasCrash = false) }
    }

    /** Aplikasi sedang tampil (onResume..onPause); cek otomatis hanya berjalan saat itu. */
    private var isVisible = false
    private var lastAutoCheckAt = 0L

    private val _eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _shizukuStatus.value = granter.status()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _shizukuStatus.value = granter.status()
    }

    init {
        viewModelScope.launch { _insights.value = withContext(Dispatchers.IO) { insightStore.all() } }
        // Jaringan berganti saat aplikasi tampil: cek ulang setelah jaringan stabil
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            repository.networkChanges.debounce(NETWORK_SETTLE_MS).collect {
                if (isVisible) autoNetworkCheck(force = true)
            }
        }
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (_: Exception) {}
    }

    private suspend fun snackbar(text: UiText) = _eventChannel.send(UiEvent.ShowSnackbar(text))

    fun refresh() {
        repository.refresh()
        _shizukuStatus.value = granter.status()
    }

    fun grantViaShizuku() {
        if (_isGranting.value) return
        viewModelScope.launch {
            _isGranting.value = true
            try {
                if (granter.status() == GranterStatus.NEEDS_AUTHORIZATION) {
                    val authorized = granter.requestPermission()
                    _shizukuStatus.value = granter.status()
                    if (!authorized) {
                        snackbar(UiText.of(R.string.grant_authorization_denied))
                        return@launch
                    }
                }

                granter.grant().fold(
                    onSuccess = { snackbar(UiText.of(R.string.grant_success)) },
                    onFailure = { error -> snackbar(error.toGrantUiText()) }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Mis. binder Shizuku mati di tengah alur; jangan biarkan crash di viewModelScope
                snackbar(e.toGrantUiText())
            } finally {
                _isGranting.value = false
                repository.refresh()
                _shizukuStatus.value = granter.status()
            }
        }
    }

    /** Pengguna akan memasukkan kode lewat notifikasi (dirender oleh `PairingNotifier`). */
    fun startNotificationActivation() = activator.awaitCode()

    /** Kode diketik langsung di aplikasi (mis. layar terpisah dengan Settings). */
    fun activateWithCode(code: String) {
        viewModelScope.launch {
            val result = activator.activate(code)
            repository.refresh()
            if (result is ActivationState.Success) snackbar(UiText.of(R.string.native_grant_success))
        }
    }

    fun applyDns(hostname: String, probe: Boolean = true) {
        viewModelScope.launch {
            _isProbing.value = true
            val result = repository.apply(hostname, probe)
            _isProbing.value = false

            when (result) {
                is ApplyResult.Success -> snackbar(
                    result.probe?.let { UiText.of(R.string.apply_success_latency, result.hostname, it.totalMs) }
                        ?: UiText.of(R.string.apply_success, result.hostname)
                )
                is ApplyResult.Failure -> snackbar(result.error.toUiText())
            }
        }
    }

    fun disableDns() {
        viewModelScope.launch {
            snackbar(UiText.of(if (repository.disable()) R.string.disable_success else R.string.disable_failed))
        }
    }

    /** Cek jaringan; DoT diuji ke resolver aktif, atau [fallbackHostname] bila proteksi mati. */
    fun runNetworkCheck(fallbackHostname: String) = runNetworkCheck(fallbackHostname, automatic = false)

    private fun runNetworkCheck(fallbackHostname: String, automatic: Boolean) {
        if (_isCheckingNetwork.value) return
        viewModelScope.launch {
            _isCheckingNetwork.value = true
            val state = dnsState.value
            val hostname = state.specifier.takeIf { state.isProtectionActive && it.isNotBlank() } ?: fallbackHostname
            val network = networkKey()
            val report = networkInspector.inspect(hostname)
            _networkReport.value = report
            _isCheckingNetwork.value = false
            recordInsight(
                InsightSample.NetworkCheck(
                    System.currentTimeMillis(), network,
                    report[NetworkCheck.INTERCEPTION], report[NetworkCheck.NXDOMAIN_HIJACK], report[NetworkCheck.DOT_REACHABLE]
                )
            )
            lastAutoCheckAt = System.currentTimeMillis()
            // Pemeriksaan manual sudah menampilkan peringatan di kartu; otomatis perlu menarik perhatian
            if (automatic && dnsState.value.isProtectionActive &&
                report[NetworkCheck.DOT_REACHABLE] == CheckResult.PROBLEM
            ) {
                _eventChannel.send(UiEvent.DotBlockedOnNetwork)
            }
        }
    }

    /** Dipanggil saat layar tampil: cek otomatis paling sering tiap [AUTO_CHECK_INTERVAL_MS], tanpa kerja latar. */
    fun onVisible() {
        isVisible = true
        autoNetworkCheck(force = false)
        if (System.currentTimeMillis() - lastStatsAt > STATS_REFRESH_MS) refreshStats()
    }

    fun onHidden() {
        isVisible = false
    }

    private fun autoNetworkCheck(force: Boolean) {
        if (_showOnboarding.value) return
        if (!force && System.currentTimeMillis() - lastAutoCheckAt < AUTO_CHECK_INTERVAL_MS) return
        runNetworkCheck(DnsManager.DEFAULT_FALLBACK_HOSTNAME, automatic = true)
        // Kartu Kesehatan juga menilai pemblokiran iklan; hanya bermakna saat proteksi aktif
        if (dnsState.value.isProtectionActive) runAudit()
        sampleLatency()
    }

    /** Tombol "Periksa ulang" di kartu Kesehatan: cek jaringan + audit sekaligus. */
    fun runHealthCheck(fallbackHostname: String) {
        runNetworkCheck(fallbackHostname)
        if (dnsState.value.isProtectionActive) runAudit()
        sampleLatency()
    }

    fun runAudit() {
        if (_isAuditing.value) return
        viewModelScope.launch {
            _isAuditing.value = true
            val state = dnsState.value
            // Saat proteksi aktif, tanya resolver langsung (DoT) untuk membedakan kebocoran dari cache DNS HP
            val direct = if (state.isProtectionActive && state.specifier.isNotBlank()) {
                async { inspector.inspect(state.specifier) }
            } else {
                null
            }
            val summary = auditor.runAudit()
            val blockedByResolver = (direct?.await() as? InspectionResult.Success)?.filter?.blocked?.toSet().orEmpty()
            _auditSummary.value = summary.reconcileWith(blockedByResolver)
            _isAuditing.value = false
        }
    }

    /**
     * Mengukur latensi DoT dan pratinjau filter (berapa domain iklan uji yang diblokir) untuk setiap
     * preset dan profil tersimpan, berurutan agar hasil latensi tidak saling mengganggu.
     */
    fun runBenchmark() {
        if (_benchmark.value.isRunning) return
        viewModelScope.launch {
            _benchmark.value = BenchmarkState(isRunning = true)
            val network = networkKey()
            val now = System.currentTimeMillis()
            for (profile in DefaultProfiles.list + profileRepository.profiles.value) {
                val result = inspector.inspect(profile.hostname)
                _benchmark.update { it.copy(results = it.results + (profile.id to result)) }
                // Disimpan untuk saran "resolver lebih cepat" di Insight
                val ms = (result as? InspectionResult.Success)?.latency?.totalMs
                withContext(Dispatchers.IO) {
                    insightStore.append(InsightSample.Latency(now, network, profile.hostname, ms, benchmark = true))
                }
            }
            _insights.value = withContext(Dispatchers.IO) { insightStore.all() }
            _benchmark.update { it.copy(isRunning = false) }
        }
    }

    fun pauseDns(minutes: Int) {
        viewModelScope.launch {
            snackbar(
                when (val result = repository.pause(minutes * 60_000L)) {
                    is PauseResult.Paused -> UiText.of(R.string.pause_success, minutes, formatTime(result.untilMs))
                    is PauseResult.Failure -> result.error.toUiText()
                }
            )
        }
    }

    fun resumeDns() {
        viewModelScope.launch {
            snackbar(
                when (val result = repository.resumeNow()) {
                    is ApplyResult.Success -> UiText.of(R.string.resume_success, result.hostname)
                    is ApplyResult.Failure -> result.error.toUiText()
                }
            )
        }
    }

    fun saveProfile(name: String, hostname: String) {
        viewModelScope.launch {
            snackbar(
                when (val result = profileRepository.add(name, hostname)) {
                    is ProfileSaveResult.Saved -> UiText.of(R.string.profile_saved, result.profile.name)
                    is ProfileSaveResult.Failure -> result.error.toUiText()
                }
            )
        }
    }

    fun deleteProfile(id: String) {
        profileRepository.remove(id)
        _benchmark.update { it.copy(results = it.results - id) }
    }

    /** Menutup onboarding; bila izin sudah ada dan [hostname] dipilih, langsung menerapkannya (dengan probe). */
    fun finishOnboarding(hostname: String?) {
        onboardingStore.markCompleted()
        _showOnboarding.value = false
        if (hostname != null && dnsState.value.hasPermission) applyDns(hostname, probe = true)
    }

    fun setAutomationEnabled(enabled: Boolean) = automationSettings.setEnabled(enabled)

    fun regenerateAutomationToken() {
        automationSettings.regenerateToken()
        viewModelScope.launch { snackbar(UiText.of(R.string.automation_token_regenerated)) }
    }

    private fun formatTime(epochMs: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMs))

    companion object {
        /** Pembanding uji stabilitas: jaringan anycast besar, jadi bila ini pun goyah, jaringannya yang bermasalah. */
        const val STABILITY_REFERENCE = "1dot1dot1dot1.cloudflare-dns.com"
        private const val AUTO_CHECK_INTERVAL_MS = 10 * 60_000L
        private const val NETWORK_SETTLE_MS = 3_000L
        private const val STATS_REFRESH_MS = 5 * 60_000L
    }

    class Factory(
        private val repository: DnsRepository,
        private val granter: PermissionGranter,
        private val profileRepository: ProfileRepository,
        private val automationSettings: AutomationSettings,
        private val onboardingStore: OnboardingStore,
        private val activator: WirelessAdbActivator,
        private val statsSettings: NextDnsStatsSettings,
        private val diagnostics: DiagnosticsCollector,
        private val insightStore: InsightStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository, granter, profileRepository, automationSettings, onboardingStore, activator, statsSettings, diagnostics, insightStore) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
