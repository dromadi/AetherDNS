package com.security.aetherdns.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.security.aetherdns.R
import com.security.aetherdns.data.LatencySummary
import com.security.aetherdns.data.NetworkKey
import com.security.aetherdns.data.NetworkSummary
import com.security.aetherdns.data.SpeedLevel
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.ui.theme.ErrorRed
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessGreen
import com.security.aetherdns.ui.theme.WarningOrange

/** Halaman Insight (tab Lainnya): kecepatan resolver per jaringan dan rapor jaringan, semuanya data lokal. */
@Composable
fun InsightContent(
    latency: List<LatencySummary>,
    networks: List<NetworkSummary>,
    onCompare: () -> Unit,
    onClear: () -> Unit
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(R.string.insight_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (latency.isEmpty() && networks.isEmpty()) {
        InsightSection {
            Text(stringResource(R.string.insight_empty), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    if (latency.isNotEmpty()) {
        InsightSection(title = stringResource(R.string.insight_speed_title)) {
            latency.forEachIndexed { index, summary ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                LatencyRow(summary)
            }
            if (latency.any { it.level == SpeedLevel.SLOW }) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    stringResource(R.string.insight_speed_slow_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onCompare, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.insight_compare))
            }
        }
    }

    if (networks.isNotEmpty()) {
        InsightSection(title = stringResource(R.string.insight_network_title)) {
            networks.forEachIndexed { index, summary ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                NetworkRow(summary)
            }
        }
    }

    TextButton(onClick = onClear) {
        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.insight_clear))
    }
}

@Composable
private fun InsightSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
private fun LatencyRow(summary: LatencySummary) {
    val color = speedColor(summary.level)
    val name = DefaultProfiles.list.firstOrNull { it.hostname.equals(summary.resolver, ignoreCase = true) }
        ?.displayName() ?: summary.resolver
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                networkLabel(summary.network),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
            Text(
                stringResource(speedLabel(summary.level)),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    val detail = stringResource(R.string.insight_speed_detail, summary.medianMs, summary.samples) +
        if (summary.failures > 0) " · " + stringResource(R.string.insight_speed_failures, summary.failures) else ""
    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (summary.daily.size > 1) {
        Spacer(modifier = Modifier.height(8.dp))
        DailyBars(summary.daily, color)
    }
}

/** Grafik batang mini median per hari; tinggi relatif terhadap hari paling lambat. */
@Composable
private fun DailyBars(values: List<Long>, color: Color) {
    val max = values.max().coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEach { value ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight((value.toFloat() / max).coerceIn(0.08f, 1f))
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(color.copy(alpha = 0.7f))
            )
        }
    }
}

@Composable
private fun NetworkRow(summary: NetworkSummary) {
    val problems = buildList {
        if (summary.tampered > 0) add(stringResource(R.string.insight_network_tampered, summary.tampered, summary.checks))
        if (summary.dotBlocked > 0) add(stringResource(R.string.insight_network_dot_blocked, summary.dotBlocked, summary.checks))
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (problems.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (problems.isEmpty()) SuccessGreen else WarningOrange,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(networkLabel(summary.network), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (problems.isEmpty()) {
                Text(
                    stringResource(R.string.insight_network_clean, summary.checks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                problems.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun networkLabel(network: NetworkKey): String = when (network.transport) {
    "wifi" -> stringResource(R.string.insight_net_wifi)
    "cellular" -> if (network.carrier.isBlank()) stringResource(R.string.insight_net_cellular)
    else stringResource(R.string.insight_net_cellular_carrier, network.carrier)
    "ethernet" -> stringResource(R.string.insight_net_ethernet)
    else -> stringResource(R.string.insight_net_other)
}

private fun speedLabel(level: SpeedLevel): Int = when (level) {
    SpeedLevel.FAST -> R.string.insight_speed_fast
    SpeedLevel.OK -> R.string.insight_speed_ok
    SpeedLevel.SLOW -> R.string.insight_speed_slow
}

@Composable
private fun speedColor(level: SpeedLevel): Color = when (level) {
    SpeedLevel.FAST -> SuccessGreen
    SpeedLevel.OK -> PrimaryBlue
    SpeedLevel.SLOW -> ErrorRed
}
