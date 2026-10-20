package com.security.aetherdns.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.aetherdns.R
import com.security.aetherdns.data.DnsState
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.shizuku.GranterStatus
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessGreen

private const val STEP_COUNT = 3

/**
 * Onboarding pertama kali: (1) apa yang dilakukan aplikasi, (2) pemberian izin, (3) pilih resolver & pasang tile.
 * Setiap langkah dapat dilewati; [onFinish] menerima hostname terpilih (diterapkan bila izin sudah ada).
 */
@Composable
fun OnboardingScreen(
    dnsState: DnsState,
    shizukuStatus: GranterStatus,
    isGranting: Boolean,
    onGrant: () -> Unit,
    onRefresh: () -> Unit,
    nativeActivation: NativeActivationUi?,
    onFinish: (hostname: String?) -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf(DefaultProfiles.list.first().id) }
    val selected = DefaultProfiles.list.first { it.id == selectedId }

    Scaffold(
        bottomBar = {
            OnboardingNavigation(
                step = step,
                canApply = dnsState.hasPermission,
                onBack = { step-- },
                onNext = { step++ },
                onSkip = { onFinish(null) },
                onFinish = { onFinish(selected.hostname) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { StepIndicator(step) }
            when (step) {
                0 -> item { WelcomeStep() }
                1 -> item {
                    StepTitle(stringResource(R.string.onboarding_permission_title), stringResource(R.string.onboarding_permission_desc))
                    Spacer(modifier = Modifier.height(12.dp))
                    if (dnsState.hasPermission) {
                        Feature(Icons.Default.CheckCircle, stringResource(R.string.onboarding_permission_granted), null, tint = SuccessGreen)
                    } else {
                        PermissionAssistantCard(
                            shizukuStatus = shizukuStatus,
                            isGranting = isGranting,
                            selectedHostname = selected.hostname,
                            onGrant = onGrant,
                            onRefresh = onRefresh,
                            nativeActivation = nativeActivation
                        )
                    }
                }
                else -> {
                    item {
                        StepTitle(stringResource(R.string.onboarding_resolver_title), stringResource(R.string.onboarding_resolver_desc))
                    }
                    items(DefaultProfiles.list, key = { it.id }) { profile ->
                        PresetCard(
                            profile = profile,
                            isSelected = profile.id == selectedId,
                            benchmarkResult = null,
                            isFastest = false,
                            onSelect = { selectedId = profile.id }
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        item { AddTileButton() }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 24.dp)
                .size(80.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(44.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        StepTitle(stringResource(R.string.onboarding_welcome_title), stringResource(R.string.onboarding_welcome_desc), center = true)
        Spacer(modifier = Modifier.height(20.dp))
        Feature(Icons.Default.BatteryChargingFull, stringResource(R.string.onboarding_feature_battery_title), stringResource(R.string.onboarding_feature_battery_desc))
        Feature(Icons.Default.VpnLock, stringResource(R.string.onboarding_feature_vpn_title), stringResource(R.string.onboarding_feature_vpn_desc))
        Feature(Icons.Default.Visibility, stringResource(R.string.onboarding_feature_scope_title), stringResource(R.string.onboarding_feature_scope_desc))
    }
}

@Composable
private fun StepTitle(title: String, description: String, center: Boolean = false) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = align, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, description: String?, tint: androidx.compose.ui.graphics.Color = PrimaryBlue) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            description?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        repeat(STEP_COUNT) { index ->
            Box(
                modifier = Modifier
                    .size(width = if (index == step) 24.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (index <= step) PrimaryBlue else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
    Text(
        text = stringResource(R.string.onboarding_step, step + 1, STEP_COUNT),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun OnboardingNavigation(
    step: Int,
    canApply: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step == 0) {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip)) }
            } else {
                TextButton(onClick = onBack) { Text(stringResource(R.string.onboarding_back)) }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (step < STEP_COUNT - 1) {
                Button(onClick = onNext) { Text(stringResource(R.string.onboarding_next)) }
            } else {
                Button(onClick = onFinish) {
                    Text(stringResource(if (canApply) R.string.onboarding_finish_apply else R.string.onboarding_finish))
                }
            }
        }
    }
}
