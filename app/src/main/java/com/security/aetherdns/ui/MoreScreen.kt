package com.security.aetherdns.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.security.aetherdns.util.DnsManager

private const val SOURCE_URL = "https://github.com/dromadi/AetherDNS"

/** Tab "Lainnya": status izin, otomasi, pengaturan DNS bawaan HP, dan tentang aplikasi. */
@Composable
fun MoreScreen(
    modifier: Modifier,
    hasPermission: Boolean,
    automation: AutomationState,
    onToggleAutomation: (Boolean) -> Unit,
    onRegenerateToken: () -> Unit,
    report: ReportUiState,
    onGenerateReport: () -> Unit,
    onClearCrash: () -> Unit,
    permissionContent: @Composable () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle(stringResource(R.string.more_permission_title))
        if (hasPermission) {
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
        } else {
            permissionContent()
        }

        AutomationCard(state = automation, onToggle = onToggleAutomation, onRegenerateToken = onRegenerateToken)

        DiagnosticsCard(state = report, onGenerate = onGenerateReport, onClearCrash = onClearCrash)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                LinkRow(Icons.Default.SettingsSuggest, stringResource(R.string.more_system_dns)) {
                    DnsManager.openSystemDnsSettings(context)
                }
                LinkRow(Icons.Default.Code, stringResource(R.string.more_source)) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.more_about_title))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.more_about_body, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = PrimaryBlue)
}

@Composable
private fun LinkRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, contentDescription = null, tint = PrimaryBlue) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
