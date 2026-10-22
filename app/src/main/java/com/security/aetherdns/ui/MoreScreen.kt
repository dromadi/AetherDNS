package com.security.aetherdns.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.security.aetherdns.BuildConfig
import com.security.aetherdns.R
import com.security.aetherdns.automation.AutomationState
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessContainer
import com.security.aetherdns.ui.theme.SuccessGreen
import com.security.aetherdns.ui.theme.WarningOrange
import com.security.aetherdns.util.DnsManager

private const val SOURCE_URL = "https://github.com/dromadi/AetherDNS"

/** Halaman di dalam tab Lainnya; null = daftar menu. State-nya di [AetherDnsApp] agar Beranda bisa membuka Insight. */
enum class MorePage(val titleRes: Int) {
    AUTOPILOT(R.string.autopilot_title),
    RULES(R.string.rules_title),
    INSIGHTS(R.string.insight_title),
    ADS_HELP(R.string.ads_help_title),
    PERMISSION(R.string.more_permission_title),
    AUTOMATION(R.string.automation_title),
    REPORT(R.string.report_title),
    ABOUT(R.string.more_about_title)
}

/** Tab "Lainnya": daftar menu ringkas; setiap item membuka halamannya sendiri. */
@Composable
fun MoreScreen(
    modifier: Modifier,
    page: MorePage?,
    onPageChange: (MorePage?) -> Unit,
    hasPermission: Boolean,
    autopilotEnabled: Boolean,
    rulesEnabled: Boolean,
    automation: AutomationState,
    onToggleAutomation: (Boolean) -> Unit,
    onRegenerateToken: () -> Unit,
    report: ReportUiState,
    onGenerateReport: () -> Unit,
    onClearCrash: () -> Unit,
    insightContent: @Composable () -> Unit,
    autopilotContent: @Composable () -> Unit,
    rulesContent: @Composable () -> Unit,
    permissionContent: @Composable () -> Unit
) {
    BackHandler(enabled = page != null) { onPageChange(null) }

    AnimatedContent(
        targetState = page,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
        label = "morePage",
        modifier = modifier
    ) { current ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (current == null) {
                MenuList(hasPermission, autopilotEnabled, rulesEnabled, automation.enabled, onPageChange)
            } else {
                PageHeader(stringResource(current.titleRes)) { onPageChange(null) }
                when (current) {
                    MorePage.AUTOPILOT -> autopilotContent()
                    MorePage.RULES -> rulesContent()
                    MorePage.INSIGHTS -> insightContent()
                    MorePage.ADS_HELP -> AdsHelpContent()
                    MorePage.PERMISSION -> if (hasPermission) PermissionGranted() else permissionContent()
                    MorePage.AUTOMATION ->
                        AutomationCard(state = automation, onToggle = onToggleAutomation, onRegenerateToken = onRegenerateToken)
                    MorePage.REPORT -> DiagnosticsCard(state = report, onGenerate = onGenerateReport, onClearCrash = onClearCrash)
                    MorePage.ABOUT -> About()
                }
            }
        }
    }
}

@Composable
private fun MenuList(hasPermission: Boolean, autopilotEnabled: Boolean, rulesEnabled: Boolean, automationEnabled: Boolean, open: (MorePage) -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            MenuItem(
                icon = Icons.Default.AutoMode,
                title = stringResource(R.string.autopilot_title),
                subtitle = stringResource(if (autopilotEnabled) R.string.autopilot_subtitle_on else R.string.autopilot_subtitle_off),
                subtitleColor = if (autopilotEnabled) SuccessGreen else null
            ) { open(MorePage.AUTOPILOT) }
            MenuItem(
                icon = Icons.Default.Rule,
                title = stringResource(R.string.rules_title),
                subtitle = stringResource(if (rulesEnabled) R.string.rules_subtitle_on else R.string.rules_subtitle_off),
                subtitleColor = if (rulesEnabled) SuccessGreen else null
            ) { open(MorePage.RULES) }
            MenuItem(
                icon = Icons.Default.Insights,
                title = stringResource(R.string.insight_title),
                subtitle = stringResource(R.string.insight_subtitle)
            ) { open(MorePage.INSIGHTS) }
            MenuItem(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = stringResource(R.string.ads_help_title),
                subtitle = stringResource(R.string.more_ads_help_subtitle)
            ) { open(MorePage.ADS_HELP) }
            MenuItem(
                icon = Icons.Default.Key,
                title = stringResource(R.string.more_permission_title),
                subtitle = stringResource(if (hasPermission) R.string.more_permission_subtitle_granted else R.string.more_permission_subtitle_missing),
                subtitleColor = if (hasPermission) SuccessGreen else WarningOrange
            ) { open(MorePage.PERMISSION) }
            MenuItem(
                icon = Icons.Default.SmartToy,
                title = stringResource(R.string.automation_title),
                subtitle = stringResource(if (automationEnabled) R.string.more_automation_subtitle_on else R.string.more_automation_subtitle_off)
            ) { open(MorePage.AUTOMATION) }
            MenuItem(
                icon = Icons.Default.BugReport,
                title = stringResource(R.string.report_title),
                subtitle = stringResource(R.string.more_report_subtitle)
            ) { open(MorePage.REPORT) }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            MenuItem(Icons.Default.SettingsSuggest, stringResource(R.string.more_system_dns), external = true) {
                DnsManager.openSystemDnsSettings(context)
            }
            MenuItem(Icons.Default.Code, stringResource(R.string.more_source), external = true) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            MenuItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.more_about_title),
                subtitle = stringResource(R.string.more_about_subtitle, BuildConfig.VERSION_NAME)
            ) { open(MorePage.ABOUT) }
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    subtitleColor: androidx.compose.ui.graphics.Color? = null,
    external: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let {
            { Text(it, color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        leadingContent = { Icon(icon, contentDescription = null, tint = PrimaryBlue) },
        trailingContent = {
            Icon(
                if (external) Icons.AutoMirrored.Filled.OpenInNew else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.more_back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun PermissionGranted() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = SuccessContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.more_permission_granted), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun About() {
    Row(verticalAlignment = Alignment.Top) {
        AetherLogo(size = 48.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            stringResource(R.string.more_about_body, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
