package com.security.aetherdns.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.security.aetherdns.R
import com.security.aetherdns.data.ChangeSource
import com.security.aetherdns.data.FasterAlternative
import com.security.aetherdns.data.HistoryAction
import com.security.aetherdns.data.HistoryEvent
import com.security.aetherdns.data.HistoryTracker
import com.security.aetherdns.data.InsightAnalyzer
import com.security.aetherdns.data.InsightSample
import com.security.aetherdns.data.LatencySummary
import com.security.aetherdns.data.NetworkKey
import com.security.aetherdns.data.NetworkSummary
import com.security.aetherdns.data.NextDnsWeek
import com.security.aetherdns.data.SpeedLevel
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.ui.theme.ErrorRed
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessGreen
import com.security.aetherdns.ui.theme.WarningOrange
import com.security.aetherdns.util.StabilityAdvice
import com.security.aetherdns.util.StabilityResult
import com.security.aetherdns.util.StabilityTest
import com.security.aetherdns.util.StabilityVerdict
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

private const val HISTORY_SHOWN = 8

/**
 * Halaman Insight (tab Lainnya): kecepatan resolver per jaringan (+ saran resolver lebih cepat), rapor jaringan,
 * riwayat proteksi, dan statistik NextDNS 7 hari. Semua kecuali NextDNS adalah data lokal.
 */
@Composable
fun InsightContent(
    insights: List<InsightSample>,
    history: List<HistoryEvent>,
    activeResolver: String?,
    currentNetwork: NetworkKey,
    nextDns: WeekUiState?,
    stability: StabilityUiState,
    now: Long,
    onRunStability: () -> Unit,
    onCompare: () -> Unit,
    onSwitch: (String) -> Unit,
    onClear: () -> Unit
) {
    var windowDays by rememberSaveable { mutableIntStateOf(7) }
    val latency = remember(insights, windowDays, now) {
        InsightAnalyzer.latency(insights, now, windowDays * InsightAnalyzer.DAY_MS)
    }
    val networks = remember(insights, now) { InsightAnalyzer.networks(insights, now) }
    val alternative = remember(insights, activeResolver, currentNetwork, now) {
        fasterAlternative(insights, activeResolver, currentNetwork, now)
    }
    val recentHistory = remember(history, now) { history.filter { now - it.timestamp <= 7 * InsightAnalyzer.DAY_MS } }

    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(R.string.insight_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    InsightSection(title = stringResource(R.string.stability_title)) {
        StabilityContent(stability, onRunStability, onSwitch)
    }

    if (latency.isEmpty() && networks.isEmpty() && recentHistory.isEmpty() && nextDns == null) {
        InsightSection {
            Text(stringResource(R.string.insight_empty), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    if (latency.isNotEmpty() || alternative != null) {
        InsightSection(
            title = stringResource(R.string.insight_speed_title),
            action = {
                listOf(7, 30).forEach { days ->
                    FilterChip(
                        selected = windowDays == days,
                        onClick = { windowDays = days },
                        label = { Text(stringResource(R.string.insight_days, days)) },
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        ) {
            latency.forEachIndexed { index, summary ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                LatencyRow(summary)
            }
            if (alternative != null) {
                Spacer(modifier = Modifier.height(12.dp))
                AlternativeBox(alternative, onSwitch)
            } else if (latency.any { it.level == SpeedLevel.SLOW }) {
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

    InsightSection(title = stringResource(R.string.insight_history_title)) {
        HistoryContent(recentHistory, now)
    }

    if (nextDns != null) {
        InsightSection(title = stringResource(R.string.insight_nextdns_title)) {
            when (nextDns) {
                WeekUiState.Idle, WeekUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                is WeekUiState.Error -> Text(
                    nextDns.error.toUiText().resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed
                )
                is WeekUiState.Loaded -> NextDnsWeekContent(nextDns.week)
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
private fun StabilityContent(state: StabilityUiState, onRun: () -> Unit, onSwitch: (String) -> Unit) {
    if (state !is StabilityUiState.Done) {
        Text(
            stringResource(R.string.stability_desc, StabilityTest.ROUNDS),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    when (state) {
        is StabilityUiState.Running -> {
            LinearProgressIndicator(
                progress = { state.round.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {}
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.stability_running, state.round, state.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is StabilityUiState.Done -> {
            StabilityAdviceBox(state.advice, onSwitch)
            Spacer(modifier = Modifier.height(12.dp))
            state.results.forEachIndexed { index, result ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                StabilityRow(result, isActive = result.resolver.equals(state.active, ignoreCase = true))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.stability_again))
            }
        }
        StabilityUiState.Idle -> Button(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.stability_run))
        }
    }
}

@Composable
private fun StabilityAdviceBox(advice: StabilityAdvice, onSwitch: (String) -> Unit) {
    val (color, text) = when (advice) {
        StabilityAdvice.ActiveStable -> SuccessGreen to stringResource(R.string.stability_advice_stable)
        StabilityAdvice.NetworkProblem -> WarningOrange to stringResource(R.string.stability_advice_network)
        StabilityAdvice.NoAlternative -> ErrorRed to stringResource(R.string.stability_advice_none)
        is StabilityAdvice.SwitchTo ->
            WarningOrange to stringResource(R.string.stability_advice_switch, resolverName(advice.resolver), advice.medianMs)
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.12f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (advice is StabilityAdvice.SwitchTo) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onSwitch(advice.resolver) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.insight_switch, resolverName(advice.resolver)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StabilityRow(result: StabilityResult, isActive: Boolean) {
    val (label, color) = when (result.verdict) {
        StabilityVerdict.STABLE -> R.string.stability_stable to SuccessGreen
        StabilityVerdict.SLOW -> R.string.stability_slow to WarningOrange
        StabilityVerdict.UNSTABLE -> R.string.stability_unstable to ErrorRed
    }
    val name = resolverName(result.resolver)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when {
                    isActive -> stringResource(R.string.stability_active, name)
                    result.resolver.equals(MainViewModel.STABILITY_REFERENCE, ignoreCase = true) ->
                        stringResource(R.string.stability_reference, name)
                    else -> name
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val median = result.medianMs
            Text(
                if (median == null) stringResource(R.string.stability_all_failed, result.attempts)
                else stringResource(R.string.stability_detail, result.failures, result.attempts, median, result.worstMs ?: median),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Pill(stringResource(label), color)
    }
}

/** Alternatif hanya dari kelompok preset yang sama, agar jenis pemblokiran tidak berubah. */
private fun fasterAlternative(
    insights: List<InsightSample>,
    activeResolver: String?,
    network: NetworkKey,
    now: Long
): FasterAlternative? {
    val resolver = activeResolver ?: return null
    val preset = DefaultProfiles.list.firstOrNull { it.hostname.equals(resolver, ignoreCase = true) } ?: return null
    val current = InsightAnalyzer.current(insights, resolver, network, now) ?: return null
    val candidates = DefaultProfiles.list.filter { it.group == preset.group }.map { it.hostname }.toSet()
    return InsightAnalyzer.fasterAlternative(insights, current, candidates, now)
}

@Composable
private fun InsightSection(
    title: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    action?.invoke(this)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
private fun resolverName(hostname: String): String =
    DefaultProfiles.list.firstOrNull { it.hostname.equals(hostname, ignoreCase = true) }?.displayName() ?: hostname

@Composable
private fun LatencyRow(summary: LatencySummary) {
    val color = speedColor(summary.level)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                resolverName(summary.resolver),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                networkLabel(summary.network),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Pill(stringResource(speedLabel(summary.level)), color)
    }
    Spacer(modifier = Modifier.height(6.dp))
    val detail = stringResource(R.string.insight_speed_detail, summary.medianMs, summary.samples) +
        if (summary.failures > 0) " · " + stringResource(R.string.insight_speed_failures, summary.failures) else ""
    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (summary.daily.size > 1) {
        Spacer(modifier = Modifier.height(8.dp))
        Bars(summary.daily.map { it to 0L }, color)
    }
}

@Composable
private fun AlternativeBox(alternative: FasterAlternative, onSwitch: (String) -> Unit) {
    val name = resolverName(alternative.resolver)
    Surface(shape = MaterialTheme.shapes.small, color = SuccessGreen.copy(alpha = 0.12f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.insight_alternative, name, alternative.factor, alternative.medianMs, alternative.currentMs),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onSwitch(alternative.resolver) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.insight_switch, name), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Grafik batang mini; tinggi relatif terhadap nilai terbesar. Bila [values] memuat bagian kedua (diblokir),
 * bagian itu diwarnai penuh di dasar batang dan sisanya diredupkan.
 */
@Composable
private fun Bars(values: List<Pair<Long, Long>>, color: Color, height: Int = 36) {
    val max = values.maxOf { it.first }.coerceAtLeast(1)
    val stacked = values.any { it.second > 0 }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEach { (value, part) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight((value.toFloat() / max).coerceIn(0.08f, 1f))
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(color.copy(alpha = if (stacked) 0.25f else 0.7f))
            ) {
                if (stacked && value > 0) {
                    val share = (part.toFloat() / value).coerceIn(0f, 1f)
                    if (share < 1f) Spacer(modifier = Modifier.weight(1f - share))
                    if (share > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(share)
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
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
private fun HistoryContent(events: List<HistoryEvent>, now: Long) {
    if (events.isEmpty()) {
        Text(
            stringResource(R.string.insight_history_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        val counts = HistoryTracker.counts(events, now, 7 * InsightAnalyzer.DAY_MS)
        val summary = HistoryAction.entries.mapNotNull { action ->
            counts[action]?.let { stringResource(countLabel(action), it) }
        }
        Text(summary.joinToString(" · "), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
        events.takeLast(HISTORY_SHOWN).asReversed().forEach { event ->
            val (icon, color) = actionIcon(event.action)
            Row(modifier = Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    val resolver = if (event.action == HistoryAction.ENABLED || event.action == HistoryAction.RESOLVER_CHANGED) {
                        " · " + resolverName(event.resolver)
                    } else {
                        ""
                    }
                    Text(
                        stringResource(actionLabel(event.action)) + resolver,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(sourceLabel(event.source)) + " · " + format.format(Date(event.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (events.any { it.source == ChangeSource.SYSTEM }) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            stringResource(R.string.insight_history_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NextDnsWeekContent(week: NextDnsWeek) {
    val format = remember { NumberFormat.getIntegerInstance() }
    val percent = if (week.total == 0L) 0 else ((week.blocked * 100) / week.total).toInt()
    Text(
        stringResource(R.string.insight_nextdns_summary, format.format(week.blocked), format.format(week.total), percent),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium
    )
    if (week.days.size > 1) {
        Spacer(modifier = Modifier.height(8.dp))
        Bars(week.days.map { it.total to it.blocked }, PrimaryBlue, height = 48)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.insight_nextdns_legend),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (week.reasons.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(stringResource(R.string.insight_nextdns_reasons), style = MaterialTheme.typography.labelLarge)
        val totalBlocked = week.reasons.sumOf { it.second }.coerceAtLeast(1)
        week.reasons.forEach { (name, queries) ->
            val share = queries.toFloat() / totalBlocked
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { share },
                    modifier = Modifier.fillMaxWidth(),
                    color = PrimaryBlue,
                    drawStopIndicator = {}
                )
            }
        }
    }
    if (week.devices.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(stringResource(R.string.insight_nextdns_devices), style = MaterialTheme.typography.labelLarge)
        week.devices.forEach { device ->
            Row(modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    device.name ?: stringResource(R.string.insight_nextdns_unknown_device),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.insight_nextdns_queries, format.format(device.queries)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

private fun countLabel(action: HistoryAction): Int = when (action) {
    HistoryAction.ENABLED -> R.string.insight_count_enabled
    HistoryAction.DISABLED -> R.string.insight_count_disabled
    HistoryAction.PAUSED -> R.string.insight_count_paused
    HistoryAction.RESUMED -> R.string.insight_count_resumed
    HistoryAction.RESOLVER_CHANGED -> R.string.insight_count_changed
}

private fun actionLabel(action: HistoryAction): Int = when (action) {
    HistoryAction.ENABLED -> R.string.insight_action_enabled
    HistoryAction.DISABLED -> R.string.insight_action_disabled
    HistoryAction.PAUSED -> R.string.insight_action_paused
    HistoryAction.RESUMED -> R.string.insight_action_resumed
    HistoryAction.RESOLVER_CHANGED -> R.string.insight_action_changed
}

private fun sourceLabel(source: ChangeSource): Int = when (source) {
    ChangeSource.APP -> R.string.insight_source_app
    ChangeSource.TILE -> R.string.insight_source_tile
    ChangeSource.SHORTCUT -> R.string.insight_source_shortcut
    ChangeSource.AUTOMATION -> R.string.insight_source_automation
    ChangeSource.SCHEDULE -> R.string.insight_source_schedule
    ChangeSource.SYSTEM -> R.string.insight_source_system
    ChangeSource.AUTOPILOT -> R.string.insight_source_autopilot
    ChangeSource.RULES -> R.string.insight_source_rules
}

@Composable
private fun actionIcon(action: HistoryAction): Pair<ImageVector, Color> = when (action) {
    HistoryAction.ENABLED -> Icons.Default.Shield to SuccessGreen
    HistoryAction.DISABLED -> Icons.Default.PowerSettingsNew to ErrorRed
    HistoryAction.PAUSED -> Icons.Default.PauseCircle to WarningOrange
    HistoryAction.RESUMED -> Icons.Default.PlayCircle to SuccessGreen
    HistoryAction.RESOLVER_CHANGED -> Icons.Default.SwapHoriz to PrimaryBlue
}
