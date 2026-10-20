package com.security.aetherdns.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.aetherdns.BuildConfig
import com.security.aetherdns.R
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.ui.theme.SuccessGreen
import com.security.aetherdns.ui.theme.WarningOrange
import com.security.aetherdns.util.DnsManager
import kotlinx.coroutines.flow.collectLatest

private const val CUSTOM_ID = "custom"

@Composable
fun AetherDnsApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val dnsState by viewModel.dnsState.collectAsState()
    val shizukuStatus by viewModel.shizukuStatus.collectAsState()
    val isProbing by viewModel.isProbing.collectAsState()
    val isGranting by viewModel.isGranting.collectAsState()
    val isAuditing by viewModel.isAuditing.collectAsState()
    val auditSummary by viewModel.auditSummary.collectAsState()
    val benchmark by viewModel.benchmark.collectAsState()
    val savedProfiles by viewModel.savedProfiles.collectAsState()
    val automation by viewModel.automation.collectAsState()
    val showOnboarding by viewModel.showOnboarding.collectAsState()
    val activationState by viewModel.activationState.collectAsState()
    val nativeActivation = if (viewModel.isNativeActivationSupported) {
        NativeActivationUi(activationState, viewModel::startNotificationActivation, viewModel::activateWithCode)
    } else {
        null
    }
    val allProfiles = DefaultProfiles.list + savedProfiles

    // Handle snackbar events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message.resolve(context))
            }
        }
    }

    var selectedProfileId by remember(dnsState.specifier, savedProfiles) {
        val matched = allProfiles.find { it.hostname.equals(dnsState.specifier, ignoreCase = true) }
        mutableStateOf(matched?.id ?: if (dnsState.specifier.isNotBlank()) CUSTOM_ID else DefaultProfiles.list.first().id)
    }
    var customHostname by remember(dnsState.specifier) {
        mutableStateOf(if (selectedProfileId == CUSTOM_ID) dnsState.specifier else "")
    }
    val selectedHostname = if (selectedProfileId == CUSTOM_ID) {
        customHostname.trim()
    } else {
        allProfiles.find { it.id == selectedProfileId }?.hostname ?: DnsManager.DEFAULT_FALLBACK_HOSTNAME
    }

    /** Memilih profil; bila proteksi sedang aktif, langsung beralih (dengan probe). */
    fun selectProfile(id: String, hostname: String) {
        selectedProfileId = id
        if (dnsState.hasPermission && dnsState.isProtectionActive) {
            viewModel.applyDns(hostname, probe = true)
        }
    }

    if (showOnboarding) {
        OnboardingScreen(
            dnsState = dnsState,
            shizukuStatus = shizukuStatus,
            isGranting = isGranting,
            onGrant = viewModel::grantViaShizuku,
            onRefresh = viewModel::refresh,
            nativeActivation = nativeActivation,
            onFinish = viewModel::finishOnboarding
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AppTopBar(hasPermission = dnsState.hasPermission) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatusCard(
                    state = dnsState,
                    isProbing = isProbing,
                    onToggle = {
                        when {
                            !dnsState.hasPermission ->
                                Toast.makeText(context, R.string.toast_permission_needed, Toast.LENGTH_SHORT).show()
                            dnsState.isProtectionActive -> viewModel.disableDns()
                            dnsState.pausedUntil != null -> viewModel.resumeDns()
                            else -> viewModel.applyDns(selectedHostname, probe = true)
                        }
                    },
                    onPause = { viewModel.pauseDns(15) }
                )
            }

            item {
                AuditCard(summary = auditSummary, isAuditing = isAuditing, onRunAudit = viewModel::runAudit)
            }

            if (!dnsState.hasPermission) {
                item {
                    PermissionAssistantCard(
                        shizukuStatus = shizukuStatus,
                        isGranting = isGranting,
                        selectedHostname = selectedHostname,
                        onGrant = viewModel::grantViaShizuku,
                        onRefresh = viewModel::refresh,
                        nativeActivation = nativeActivation
                    )
                }
            }

            item {
                PresetHeader(isBenchmarkRunning = benchmark.isRunning, onRunBenchmark = viewModel::runBenchmark)
            }

            items(DefaultProfiles.list, key = { it.id }) { profile ->
                PresetCard(
                    profile = profile,
                    isSelected = selectedProfileId == profile.id,
                    benchmarkResult = benchmark.results[profile.id],
                    isFastest = benchmark.fastestId == profile.id,
                    onSelect = { selectProfile(profile.id, profile.hostname) }
                )
            }

            items(savedProfiles, key = { it.id }) { profile ->
                SavedProfileCard(
                    profile = profile,
                    isSelected = selectedProfileId == profile.id,
                    benchmarkResult = benchmark.results[profile.id],
                    isFastest = benchmark.fastestId == profile.id,
                    onSelect = { selectProfile(profile.id, profile.hostname) },
                    onDelete = { viewModel.deleteProfile(profile.id) }
                )
            }

            item {
                CustomHostnameCard(
                    isSelected = selectedProfileId == CUSTOM_ID,
                    hostname = customHostname,
                    onHostnameChange = { customHostname = it },
                    onSelect = { selectedProfileId = CUSTOM_ID },
                    canApply = dnsState.hasPermission && dnsState.isProtectionActive,
                    isProbing = isProbing,
                    onApply = { viewModel.applyDns(customHostname.trim(), probe = true) },
                    onSaveProfile = { name -> viewModel.saveProfile(name, customHostname) }
                )
            }

            item {
                AutomationCard(
                    state = automation,
                    onToggle = viewModel::setAutomationEnabled,
                    onRegenerateToken = viewModel::regenerateAutomationToken
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(hasPermission: Boolean) {
    TopAppBar(
        title = {
            Column {
                Text(text = stringResource(R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    text = stringResource(R.string.app_subtitle, BuildConfig.VERSION_NAME),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            val color = if (hasPermission) SuccessGreen else WarningOrange
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(if (hasPermission) R.string.badge_native_active else R.string.badge_needs_permission),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                }
            }
        }
    )
}
