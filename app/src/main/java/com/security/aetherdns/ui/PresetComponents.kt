package com.security.aetherdns.ui

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.aetherdns.R
import com.security.aetherdns.model.DnsProfile
import com.security.aetherdns.ui.theme.ErrorRed
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessGreen
import com.security.aetherdns.util.HostnameValidator
import com.security.aetherdns.util.InspectionResult

/** Judul tab Resolver + tombol "Uji Kecepatan & Filter" selebar layar. */
@Composable
fun PresetHeader(isBenchmarkRunning: Boolean, onRunBenchmark: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.presets_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.presets_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        FilledTonalButton(
            onClick = onRunBenchmark,
            enabled = !isBenchmarkRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isBenchmarkRunning) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(if (isBenchmarkRunning) R.string.benchmark_running else R.string.benchmark_run))
        }
    }
}

/** Judul grup preset (Blokir iklan / Keluarga / Keamanan saja / Enkripsi saja) dengan penjelasan singkat. */
@Composable
fun PresetGroupHeader(title: String, description: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryBlue)
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PresetCard(
    profile: DnsProfile,
    isSelected: Boolean,
    benchmarkResult: InspectionResult?,
    isFastest: Boolean,
    onSelect: () -> Unit
) {
    SelectableCard(isSelected = isSelected, onClick = onSelect) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = profile.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (profile.categoryRes != 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = stringResource(profile.categoryRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (profile.descriptionRes != 0) {
                    Text(
                        text = stringResource(profile.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                HostnameText(profile.hostname)
                benchmarkResult?.let { BenchmarkChip(result = it, isFastest = isFastest) }
            }
            Spacer(modifier = Modifier.width(12.dp))
            SelectionIndicator(isSelected)
        }
    }
}

/** Penanda pilihan bulat (pengganti RadioButton 48dp agar kartu lebih ringkas). */
@Composable
fun SelectionIndicator(isSelected: Boolean) {
    val color by animateColorAsState(
        if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.outline,
        label = "selectionIndicator"
    )
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(if (isSelected) Modifier.background(color) else Modifier.border(2.dp, color, CircleShape)),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

/** Kartu dengan sorotan border saat terpilih; dipakai preset, profil tersimpan, dan hostname kustom. */
@Composable
fun SelectableCard(isSelected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val container by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "selectableContainer"
    )
    val border by animateColorAsState(
        if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.outlineVariant,
        label = "selectableBorder"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, border)
    ) {
        content()
    }
}

@Composable
fun HostnameText(hostname: String) {
    Text(
        text = hostname,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** Hasil uji per resolver: latensi DoT + jumlah domain iklan uji yang diblokir. */
@Composable
fun BenchmarkChip(result: InspectionResult, isFastest: Boolean) {
    val (text, color) = when (result) {
        is InspectionResult.Success -> {
            val label = stringResource(
                R.string.benchmark_chip_success,
                result.latency.totalMs,
                result.filter.blocked.size,
                result.filter.tested
            )
            val withBadge = if (isFastest) stringResource(R.string.benchmark_chip_fastest, label) else label
            withBadge to if (isFastest) SuccessGreen else PrimaryBlue
        }
        is InspectionResult.Failure ->
            stringResource(R.string.benchmark_chip_failed, result.failure.toUiText().resolve()) to ErrorRed
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Opsi hostname kustom: input FQDN tervalidasi, helper NextDNS, simpan profil, dan uji & terapkan. */
@Composable
fun CustomHostnameCard(
    isSelected: Boolean,
    hostname: String,
    onHostnameChange: (String) -> Unit,
    onSelect: () -> Unit,
    canApply: Boolean,
    isProbing: Boolean,
    onApply: () -> Unit,
    onSaveProfile: (name: String) -> Unit
) {
    val context = LocalContext.current
    val validation = remember(hostname) {
        if (hostname.isBlank()) null else HostnameValidator.validate(hostname)
    }
    val error = (validation as? HostnameValidator.Result.Invalid)?.error?.toUiText()?.resolve()

    SelectableCard(isSelected = isSelected, onClick = onSelect) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.custom_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.custom_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                SelectionIndicator(isSelected)
            }

            if (isSelected) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = hostname,
                    onValueChange = onHostnameChange,
                    label = { Text(stringResource(R.string.custom_field_label), fontSize = 12.sp) },
                    placeholder = { Text(stringResource(R.string.custom_field_placeholder), fontSize = 12.sp) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        if (error != null) Text(error, color = ErrorRed, fontSize = 12.sp)
                        else Text(stringResource(R.string.custom_field_hint), fontSize = 12.sp)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                NextDnsHelper(onUseHostname = onHostnameChange)

                if (validation is HostnameValidator.Result.Valid) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SaveProfileRow(onSave = onSaveProfile)
                }

                if (canApply && hostname.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (error != null) {
                                Toast.makeText(context, context.getString(R.string.toast_hostname_invalid, error), Toast.LENGTH_SHORT).show()
                            } else {
                                onApply()
                            }
                        },
                        enabled = error == null && !isProbing,
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isProbing) {
                            CircularProgressIndicator(color = LocalContentColor.current, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.common_testing), fontSize = 12.sp)
                        } else {
                            Text(stringResource(R.string.custom_apply), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
