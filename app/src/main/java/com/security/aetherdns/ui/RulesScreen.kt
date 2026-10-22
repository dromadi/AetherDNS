package com.security.aetherdns.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.security.aetherdns.R
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.model.DnsProfile
import com.security.aetherdns.model.PresetGroup
import com.security.aetherdns.rules.AppliedRule
import com.security.aetherdns.rules.NetworkType
import com.security.aetherdns.rules.RuleSet
import com.security.aetherdns.rules.RuleSource
import com.security.aetherdns.rules.RuleTarget
import com.security.aetherdns.rules.RulesStatus
import com.security.aetherdns.rules.ScheduleRule
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessGreen
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** Halaman Aturan Pintar (tab Lainnya): sakelar, aturan per jaringan, dan jadwal. */
@Composable
fun RulesContent(
    status: RulesStatus,
    profiles: List<DnsProfile>,
    onUpdate: (RuleSet) -> Unit
) {
    val rules = status.rules
    var showDialog by remember { mutableStateOf(false) }

    RulesCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = PrimaryBlue)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.rules_switch),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = rules.enabled, onCheckedChange = { onUpdate(rules.copy(enabled = it)) })
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.rules_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (rules.enabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(shape = MaterialTheme.shapes.small, color = SuccessGreen.copy(alpha = 0.12f)) {
                Text(rulesNow(status.applied), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.rules_main_choice, status.base?.let { resolverLabel(it, profiles) } ?: "—"),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            stringResource(R.string.rules_main_choice_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    RulesCard(title = stringResource(R.string.rules_network_title)) {
        NetworkRuleRow(Icons.Default.Wifi, stringResource(R.string.rules_wifi), rules.wifi, profiles) {
            onUpdate(rules.copy(wifi = it))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        NetworkRuleRow(Icons.Default.SignalCellularAlt, stringResource(R.string.rules_cellular), rules.cellular, profiles) {
            onUpdate(rules.copy(cellular = it))
        }
    }

    RulesCard(title = stringResource(R.string.rules_schedule_title)) {
        Text(
            stringResource(R.string.rules_schedule_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (rules.schedules.isEmpty()) {
            Text(stringResource(R.string.rules_schedule_empty), style = MaterialTheme.typography.bodyMedium)
        }
        rules.schedules.forEach { schedule ->
            ScheduleRow(schedule, profiles) { onUpdate(rules.copy(schedules = rules.schedules - schedule)) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = { showDialog = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.rules_add_schedule))
            }
        }
        val familyName = stringResource(R.string.rules_schedule_name_family)
        if (rules.schedules.none { it.hostname == FAMILY_HOST }) {
            OutlinedButton(
                onClick = {
                    val family = ScheduleRule(UUID.randomUUID().toString(), familyName, DayOfWeek.entries.toSet(), 21 * 60, 6 * 60, FAMILY_HOST)
                    onUpdate(rules.copy(enabled = true, schedules = rules.schedules + family))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.rules_template_family) + " (" + timeLabel(21 * 60) + "–" + timeLabel(6 * 60) + ")")
            }
        }
    }

    if (showDialog) {
        ScheduleDialog(
            profiles = profiles,
            onDismiss = { showDialog = false },
            onSave = {
                showDialog = false
                onUpdate(rules.copy(enabled = true, schedules = rules.schedules + it))
            }
        )
    }
}

/** Catatan aturan yang sedang diterapkan untuk kartu status Beranda. */
@Composable
fun rulesNote(applied: AppliedRule?): String? = when (val source = applied?.source) {
    null -> null
    is RuleSource.Schedule -> stringResource(R.string.status_rule_schedule, source.rule.name)
    is RuleSource.Network -> stringResource(R.string.status_rule_network, networkTypeLabel(source.type))
}

@Composable
private fun rulesNow(applied: AppliedRule?): String {
    val profiles = DefaultProfiles.list
    return when (val source = applied?.source) {
        null -> stringResource(R.string.rules_now_base)
        is RuleSource.Schedule ->
            stringResource(R.string.rules_now_schedule, source.rule.name, resolverLabel(applied.hostname, profiles))
        is RuleSource.Network ->
            stringResource(R.string.rules_now_network, networkTypeLabel(source.type), resolverLabel(applied.hostname, profiles))
    }
}

@Composable
private fun networkTypeLabel(type: NetworkType): String = when (type) {
    NetworkType.WIFI -> stringResource(R.string.rules_wifi)
    NetworkType.CELLULAR -> stringResource(R.string.rules_cellular)
    NetworkType.OTHER -> stringResource(R.string.insight_net_other)
}

@Composable
private fun resolverLabel(hostname: String, profiles: List<DnsProfile>): String =
    (profiles + DefaultProfiles.list).firstOrNull { it.hostname.equals(hostname, ignoreCase = true) }?.displayName() ?: hostname

@Composable
private fun NetworkRuleRow(
    icon: ImageVector,
    label: String,
    target: RuleTarget?,
    profiles: List<DnsProfile>,
    onChange: (RuleTarget?) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TargetPicker(target, profiles, allowFastest = true, onChange = onChange)
    }
}

/** Pemilih tujuan: pilihan utama (null), tercepat, atau resolver tertentu. */
@Composable
private fun TargetPicker(
    target: RuleTarget?,
    profiles: List<DnsProfile>,
    allowFastest: Boolean,
    onChange: (RuleTarget?) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val label = when (target) {
        null -> stringResource(R.string.rules_target_none)
        RuleTarget.Fastest -> stringResource(R.string.rules_target_fastest_short)
        is RuleTarget.Resolver -> resolverLabel(target.hostname, profiles)
    }
    Box {
        OutlinedButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowFastest) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rules_target_none)) }, onClick = { open = false; onChange(null) })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rules_target_fastest)) },
                    onClick = { open = false; onChange(RuleTarget.Fastest) }
                )
                HorizontalDivider()
            }
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.displayName()) },
                    onClick = { open = false; onChange(RuleTarget.Resolver(profile.hostname)) }
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(schedule: ScheduleRule, profiles: List<DnsProfile>, onDelete: () -> Unit) {
    Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Schedule, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(schedule.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                stringResource(
                    R.string.rules_schedule_summary,
                    daysLabel(schedule.days),
                    timeLabel(schedule.startMinute),
                    timeLabel(schedule.endMinute)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.rules_schedule_resolver, resolverLabel(schedule.hostname, profiles)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.rules_delete_schedule),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(profiles: List<DnsProfile>, onDismiss: () -> Unit, onSave: (ScheduleRule) -> Unit) {
    var name by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(DayOfWeek.entries.toSet()) }
    var start by remember { mutableIntStateOf(21 * 60) }
    var end by remember { mutableIntStateOf(6 * 60) }
    var hostname by remember { mutableStateOf(FAMILY_HOST) }
    var editing by remember { mutableStateOf<Boolean?>(null) } // true = mulai, false = selesai

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.rules_dialog_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.rules_dialog_days), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekOrder().forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.rules_dialog_start) + " " + timeLabel(start))
                    }
                    OutlinedButton(onClick = { editing = false }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.rules_dialog_end) + " " + timeLabel(end))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rules_dialog_resolver), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TargetPicker(RuleTarget.Resolver(hostname), profiles, allowFastest = false) {
                        (it as? RuleTarget.Resolver)?.let { r -> hostname = r.hostname }
                    }
                }
            }
        },
        confirmButton = {
            val fallbackName = resolverLabel(hostname, profiles)
            TextButton(
                onClick = {
                    onSave(ScheduleRule(UUID.randomUUID().toString(), name.trim().ifEmpty { fallbackName }, days, start, end, hostname))
                },
                enabled = days.isNotEmpty() && start != end
            ) { Text(stringResource(R.string.rules_dialog_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rules_dialog_cancel)) } }
    )

    editing?.let { isStart ->
        val initial = if (isStart) start else end
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                TextButton(onClick = {
                    val minute = state.hour * 60 + state.minute
                    if (isStart) start = minute else end = minute
                    editing = null
                }) { Text(stringResource(R.string.rules_dialog_ok)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.rules_dialog_cancel)) } },
            text = { TimePicker(state = state) }
        )
    }
}

@Composable
private fun RulesCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
private fun daysLabel(days: Set<DayOfWeek>): String =
    if (days.size == 7) stringResource(R.string.rules_every_day)
    else weekOrder().filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

/** Senin-pertama, seperti kalender Indonesia dan ISO-8601. */
private fun weekOrder(): List<DayOfWeek> = DayOfWeek.entries

private fun timeLabel(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private val FAMILY_HOST = DefaultProfiles.list.first { it.group == PresetGroup.FAMILY }.hostname
