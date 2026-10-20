package com.security.aetherdns.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

/** Judul daftar preset + tombol "Uji Kecepatan & Filter". */
@Composable
fun PresetHeader(isBenchmarkRunning: Boolean, onRunBenchmark: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Tune, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.presets_title),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRunBenchmark, enabled = !isBenchmarkRunning) {
            if (isBenchmarkRunning) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                stringResource(if (isBenchmarkRunning) R.string.benchmark_running else R.string.benchmark_run),
                fontSize = 12.sp
            )
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
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = onSelect)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = profile.displayName(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
                    if (profile.categoryRes != 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                text = stringResource(profile.categoryRes),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (profile.descriptionRes != 0) {
                    Text(
                        text = stringResource(profile.descriptionRes),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                HostnameText(profile.hostname)
                benchmarkResult?.let { BenchmarkChip(result = it, isFastest = isFastest) }
            }
        }
    }
}

/** Kartu dengan sorotan border saat terpilih; dipakai preset, profil tersimpan, dan hostname kustom. */
@Composable
fun SelectableCard(isSelected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PrimaryBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(1.5.dp, PrimaryBlue) else null
    ) {
        content()
    }
}

@Composable
fun HostnameText(hostname: String) {
    Text(
        text = hostname,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = PrimaryBlue,
        modifier = Modifier.padding(top = 4.dp)
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
            fontSize = 10.sp,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isSelected, onClick = onSelect)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.custom_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(stringResource(R.string.custom_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                        if (error != null) Text(error, color = ErrorRed, fontSize = 11.sp)
                        else Text(stringResource(R.string.custom_field_hint), fontSize = 11.sp)
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
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
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
