package com.security.aetherdns.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.security.aetherdns.data.ApplyResult
import com.security.aetherdns.data.DnsRepository
import com.security.aetherdns.data.DnsState
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.shizuku.GranterStatus
import com.security.aetherdns.shizuku.PermissionGranter
import com.security.aetherdns.util.AuditSummary
import com.security.aetherdns.util.DnsAuditor
import com.security.aetherdns.util.DotProbe
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.DotProber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

data class BenchmarkState(
    val isRunning: Boolean = false,
    val results: Map<String, DotProbeResult> = emptyMap()
) {
    /** ID profil dengan total latensi terendah di antara probe yang berhasil. */
    val fastestId: String?
        get() = results.entries
            .mapNotNull { (id, r) -> (r as? DotProbeResult.Success)?.let { id to it.totalMs } }
            .minByOrNull { it.second }?.first
}

class MainViewModel(
    private val repository: DnsRepository,
    private val granter: PermissionGranter,
    private val prober: DotProber = DotProbe
) : ViewModel() {

    private val _benchmark = MutableStateFlow(BenchmarkState())
    val benchmark: StateFlow<BenchmarkState> = _benchmark.asStateFlow()

    val dnsState: StateFlow<DnsState> = repository.state

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

    private val _eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _shizukuStatus.value = granter.status()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _shizukuStatus.value = granter.status()
    }

    init {
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
                        _eventChannel.send(UiEvent.ShowSnackbar("Otorisasi Shizuku ditolak atau Shizuku berhenti."))
                        return@launch
                    }
                }

                granter.grant().fold(
                    onSuccess = {
                        _eventChannel.send(UiEvent.ShowSnackbar("Izin WRITE_SECURE_SETTINGS berhasil diberikan via Shizuku!"))
                    },
                    onFailure = { error ->
                        _eventChannel.send(UiEvent.ShowSnackbar("Gagal: ${error.message}"))
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Mis. binder Shizuku mati di tengah alur; jangan biarkan crash di viewModelScope
                _eventChannel.send(UiEvent.ShowSnackbar("Gagal: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                _isGranting.value = false
                repository.refresh()
                _shizukuStatus.value = granter.status()
            }
        }
    }

    fun applyDns(hostname: String, probe: Boolean = true) {
        viewModelScope.launch {
            _isProbing.value = true
            val result = repository.apply(hostname, probe)
            _isProbing.value = false

            when (result) {
                is ApplyResult.Success -> {
                    val latency = result.probe?.let { " (${it.totalMs} ms)" } ?: ""
                    _eventChannel.send(UiEvent.ShowSnackbar("Proteksi DNS berhasil diaktifkan ke ${result.hostname}$latency"))
                }
                is ApplyResult.Failure -> {
                    _eventChannel.send(UiEvent.ShowSnackbar(result.reason))
                }
            }
        }
    }

    fun disableDns() {
        viewModelScope.launch {
            val success = repository.disable()
            if (success) {
                _eventChannel.send(UiEvent.ShowSnackbar("Proteksi DNS dinonaktifkan"))
            } else {
                _eventChannel.send(UiEvent.ShowSnackbar("Gagal menonaktifkan DNS"))
            }
        }
    }

    fun runAudit() {
        viewModelScope.launch {
            _isAuditing.value = true
            _auditSummary.value = DnsAuditor.runAudit()
            _isAuditing.value = false
        }
    }

    /** Mengukur latensi DoT (handshake + query) setiap preset secara berurutan agar hasil tidak saling mengganggu. */
    fun runBenchmark() {
        if (_benchmark.value.isRunning) return
        viewModelScope.launch {
            _benchmark.value = BenchmarkState(isRunning = true)
            for (profile in DefaultProfiles.list) {
                val result = prober.probe(profile.hostname)
                _benchmark.update { it.copy(results = it.results + (profile.id to result)) }
            }
            _benchmark.update { it.copy(isRunning = false) }
        }
    }

    class Factory(
        private val repository: DnsRepository,
        private val granter: PermissionGranter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository, granter) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
