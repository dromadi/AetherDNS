package com.security.aetherdns.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.security.aetherdns.data.ApplyResult
import com.security.aetherdns.data.DnsRepository
import com.security.aetherdns.data.DnsState
import com.security.aetherdns.shizuku.GranterStatus
import com.security.aetherdns.shizuku.PermissionGranter
import com.security.aetherdns.util.AuditSummary
import com.security.aetherdns.util.DnsAuditor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

class MainViewModel(
    private val repository: DnsRepository,
    private val granter: PermissionGranter
) : ViewModel() {

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
        viewModelScope.launch {
            _isGranting.value = true
            val currentStatus = granter.status()

            if (currentStatus == GranterStatus.NEEDS_AUTHORIZATION) {
                val granted = granter.requestPermission()
                _shizukuStatus.value = granter.status()
                if (!granted) {
                    _isGranting.value = false
                    _eventChannel.send(UiEvent.ShowSnackbar("Otorisasi Shizuku ditolak oleh pengguna."))
                    return@launch
                }
            }

            val result = granter.grant()
            _isGranting.value = false

            result.fold(
                onSuccess = {
                    repository.refresh()
                    _shizukuStatus.value = granter.status()
                    _eventChannel.send(UiEvent.ShowSnackbar("Izin WRITE_SECURE_SETTINGS berhasil diberikan via Shizuku!"))
                },
                onFailure = { error ->
                    _eventChannel.send(UiEvent.ShowSnackbar("Gagal: ${error.message}"))
                }
            )
        }
    }

    fun applyDns(hostname: String, probe: Boolean = true) {
        viewModelScope.launch {
            _isProbing.value = true
            val result = repository.apply(hostname, probe)
            _isProbing.value = false

            when (result) {
                is ApplyResult.Success -> {
                    _eventChannel.send(UiEvent.ShowSnackbar("Proteksi DNS berhasil diaktifkan ke ${result.hostname}"))
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
