package com.security.aetherdns.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.security.aetherdns.R
import com.security.aetherdns.autopilot.AutopilotHold
import com.security.aetherdns.autopilot.AutopilotReason
import com.security.aetherdns.autopilot.AutopilotStatus
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.ui.theme.ErrorRed
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessGreen
import com.security.aetherdns.ui.theme.WarningOrange

/** Halaman Autopilot (tab Lainnya): sakelar, apa yang dilakukan, dan tindakan yang sedang berlaku. */
@Composable
fun AutopilotContent(
    status: AutopilotStatus,
    canNotify: Boolean,
    onToggle: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit
) {
    PageCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoMode, contentDescription = null, tint = PrimaryBlue)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.autopilot_switch),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = status.enabled, onCheckedChange = onToggle)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.autopilot_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (status.enabled) {
            Spacer(modifier = Modifier.height(12.dp))
            val (note, color) = autopilotNow(status.hold)
            Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.12f)) {
                Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
            if (!canNotify) {
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.autopilot_notif_permission))
                }
            }
        }
    }

    PageCard {
        InfoRow(Icons.Default.Wifi, stringResource(R.string.autopilot_does_captive))
        InfoRow(Icons.Default.WifiOff, stringResource(R.string.autopilot_does_blocked))
        InfoRow(Icons.Default.SwapHoriz, stringResource(R.string.autopilot_does_switch))
        InfoRow(Icons.Default.ThumbUp, stringResource(R.string.autopilot_respects))
    }
}

/** Ringkasan tindakan Autopilot yang sedang berlaku, untuk halaman Autopilot dan kartu status. */
@Composable
fun autopilotNote(hold: AutopilotHold?): String? {
    hold ?: return null
    return if (hold.substitute != null) {
        stringResource(R.string.status_autopilot_switched, resolverDisplayName(hold.substitute))
    } else {
        stringResource(R.string.status_autopilot_paused, reasonText(hold))
    }
}

@Composable
private fun autopilotNow(hold: AutopilotHold?): Pair<String, Color> = when {
    hold == null -> stringResource(R.string.autopilot_now_idle) to SuccessGreen
    hold.substitute != null -> stringResource(
        R.string.autopilot_now_switched,
        resolverDisplayName(hold.substitute),
        resolverDisplayName(hold.original)
    ) to WarningOrange
    else -> stringResource(R.string.autopilot_now_paused, reasonText(hold)) to WarningOrange
}

@Composable
private fun reasonText(hold: AutopilotHold): String = when (hold.reason) {
    AutopilotReason.CAPTIVE_PORTAL -> stringResource(R.string.autopilot_reason_captive)
    AutopilotReason.DOT_BLOCKED -> stringResource(R.string.autopilot_reason_blocked)
    AutopilotReason.RESOLVER_DOWN -> stringResource(R.string.autopilot_reason_down, resolverDisplayName(hold.original))
}

@Composable
private fun resolverDisplayName(hostname: String): String {
    val context = LocalContext.current
    return DefaultProfiles.list.firstOrNull { it.hostname.equals(hostname, ignoreCase = true) }?.displayName(context) ?: hostname
}

/** Halaman "Kenapa masih ada iklan?": batas pemblokiran DNS dijelaskan dengan jujur, plus jalan keluarnya. */
@Composable
fun AdsHelpContent() {
    Text(
        stringResource(R.string.ads_help_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    PageCard {
        HelpItem(Icons.Default.CheckCircle, SuccessGreen, R.string.ads_help_can_title, R.string.ads_help_can_body)
    }
    PageCard {
        HelpItem(Icons.Default.OndemandVideo, ErrorRed, R.string.ads_help_video_title, R.string.ads_help_video_body)
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        HelpItem(Icons.Default.Widgets, WarningOrange, R.string.ads_help_inhouse_title, R.string.ads_help_inhouse_body)
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        HelpItem(Icons.Default.Cached, WarningOrange, R.string.ads_help_cache_title, R.string.ads_help_cache_body)
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        HelpItem(Icons.Default.Language, WarningOrange, R.string.ads_help_browser_title, R.string.ads_help_browser_body)
    }
    PageCard {
        HelpItem(Icons.Default.Tune, PrimaryBlue, R.string.ads_help_more_title, R.string.ads_help_more_body)
    }
}

@Composable
private fun PageCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HelpItem(icon: ImageVector, color: Color, title: Int, body: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
