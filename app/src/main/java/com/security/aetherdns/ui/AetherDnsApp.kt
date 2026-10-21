package com.security.aetherdns.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.security.aetherdns.util.NextDnsHostname
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val CUSTOM_ID = "custom"

@OptIn(ExperimentalMaterial3Api::class)
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
    val networkReport by viewModel.networkReport.collectAsState()
    val isCheckingNetwork by viewModel.isCheckingNetwork.collectAsState()
    val statsApiKey by viewModel.statsApiKey.collectAsState()
    val stats by viewModel.stats.collectAsState()
    // Statistik hanya bermakna untuk profil NextDNS pribadi yang sedang aktif
    val activeNextDnsProfile = dnsState.specifier.takeIf { dnsState.isProtectionActive }
        ?.let { NextDnsHostname.extractConfigId(it) }
    LaunchedEffect(activeNextDnsProfile, statsApiKey) {
        if (activeNextDnsProfile != null && statsApiKey != null) viewModel.refreshStats()
    }
    val activationState by viewModel.activationState.collectAsState()
    val nativeActivation = if (viewModel.isNativeActivationSupported) {
        NativeActivationUi(activationState, viewModel::startNotificationActivation, viewModel::activateWithCode)
    } else {
        null
    }
    val allProfiles = DefaultProfiles.list + savedProfiles

    var showDotBlockedDialog by rememberSaveable { mutableStateOf(false) }

    // Handle snackbar events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message.resolve(context))
                UiEvent.DotBlockedOnNetwork -> showDotBlockedDialog = true
            }
        }
    }

    if (showDotBlockedDialog) {
        DotBlockedDialog(
            onPause = {
                showDotBlockedDialog = false
                viewModel.pauseDns(15)
            },
            onDismiss = { showDotBlockedDialog = false }
        )
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

    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    val homeListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val selectedProfile = allProfiles.find { it.id == selectedProfileId }
    val resolverName = selectedProfile?.displayName() ?: stringResource(R.string.home_custom_resolver)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AppTopBar(hasPermission = dnsState.hasPermission) },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            BadgedBox(badge = {
                                if (item == AppTab.HOME && !dnsState.hasPermission) Badge()
                            }) {
                                Icon(if (tab == item) item.selectedIcon else item.icon, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(item.label)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "tabContent"
        ) { currentTab ->
        when (currentTab) {
            AppTab.HOME -> PullToRefreshBox(
                isRefreshing = false,
                onRefresh = viewModel::refresh,
                modifier = contentModifier
            ) {
                LazyColumn(
                    state = homeListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        StatusCard(
                            state = dnsState,
                            resolverName = resolverName,
                            isProbing = isProbing,
                            onToggle = {
                                when {
                                    // Arahkan ke asisten izin tepat di bawah kartu ini
                                    !dnsState.hasPermission -> scope.launch { homeListState.animateScrollToItem(1) }
                                    dnsState.isProtectionActive -> viewModel.disableDns()
                                    dnsState.pausedUntil != null -> viewModel.resumeDns()
                                    else -> viewModel.applyDns(selectedHostname, probe = true)
                                }
                            },
                            onPause = { viewModel.pauseDns(15) }
                        )
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
                        SelectedResolverCard(
                            name = resolverName,
                            hostname = selectedHostname,
                            benchmarkResult = selectedProfile?.let { benchmark.results[it.id] },
                            onChange = { tab = AppTab.RESOLVERS }
                        )
                    }

                    if (activeNextDnsProfile != null) {
                        item {
                            BlockStatsCard(
                                hasApiKey = statsApiKey != null,
                                state = stats,
                                onSaveKey = viewModel::saveStatsApiKey,
                                onRemoveKey = viewModel::clearStatsApiKey,
                                onRefresh = viewModel::refreshStats
                            )
                        }
                    }

                    item {
                        NetworkCheckCard(
                            report = networkReport,
                            isChecking = isCheckingNetwork,
                            isProtectionActive = dnsState.isProtectionActive,
                            onRun = { viewModel.runNetworkCheck(selectedHostname.ifBlank { DnsManager.DEFAULT_FALLBACK_HOSTNAME }) },
                            onPause = { viewModel.pauseDns(15) }
                        )
                    }

                    item {
                        AuditCard(summary = auditSummary, isAuditing = isAuditing, onRunAudit = viewModel::runAudit)
                    }
                }
            }

            AppTab.RESOLVERS -> LazyColumn(
                modifier = contentModifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PresetHeader(isBenchmarkRunning = benchmark.isRunning, onRunBenchmark = viewModel::runBenchmark)
                }

                DefaultProfiles.grouped.forEach { (group, profiles) ->
                    item(key = "group_${group.name}") {
                        PresetGroupHeader(stringResource(group.titleRes), stringResource(group.descriptionRes))
                    }
                    items(profiles, key = { it.id }) { profile ->
                        PresetCard(
                            profile = profile,
                            isSelected = selectedProfileId == profile.id,
                            benchmarkResult = benchmark.results[profile.id],
                            isFastest = benchmark.fastestId == profile.id,
                            onSelect = { selectProfile(profile.id, profile.hostname) }
                        )
                    }
                }

                if (savedProfiles.isNotEmpty()) {
                    item(key = "group_saved") {
                        PresetGroupHeader(stringResource(R.string.preset_group_saved), null)
                    }
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
            }

            AppTab.MORE -> MoreScreen(
                modifier = contentModifier,
                hasPermission = dnsState.hasPermission,
                automation = automation,
                onToggleAutomation = viewModel::setAutomationEnabled,
                onRegenerateToken = viewModel::regenerateAutomationToken,
                permissionContent = {
                    PermissionAssistantCard(
                        shizukuStatus = shizukuStatus,
                        isGranting = isGranting,
                        selectedHostname = selectedHostname,
                        onGrant = viewModel::grantViaShizuku,
                        onRefresh = viewModel::refresh,
                        nativeActivation = nativeActivation
                    )
                }
            )
        }
        }
    }
}

private enum class AppTab(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(R.string.tab_home, Icons.Outlined.Shield, Icons.Filled.Shield),
    RESOLVERS(R.string.tab_resolvers, Icons.Outlined.Dns, Icons.Filled.Dns),
    MORE(R.string.tab_more, Icons.Outlined.Settings, Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(hasPermission: Boolean) {
    TopAppBar(
        navigationIcon = { AetherLogo(size = 36.dp, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) },
        title = {
            Column {
                Text(text = stringResource(R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                }
            }
        }
    )
}
