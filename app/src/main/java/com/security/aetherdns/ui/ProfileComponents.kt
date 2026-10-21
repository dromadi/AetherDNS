package com.security.aetherdns.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.aetherdns.R
import com.security.aetherdns.model.DnsProfile
import com.security.aetherdns.ui.theme.ErrorRed
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.util.InspectionResult
import com.security.aetherdns.util.NextDnsHostname

/** Kartu profil kustom tersimpan, dengan tombol hapus. */
@Composable
fun SavedProfileCard(
    profile: DnsProfile,
    isSelected: Boolean,
    benchmarkResult: InspectionResult?,
    isFastest: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.profile_delete_confirm_title)) },
            text = { Text(stringResource(R.string.profile_delete_confirm_body, profile.displayName())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.common_delete), color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    SelectableCard(isSelected = isSelected, onClick = onSelect) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = onSelect)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(profile.displayName(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                HostnameText(profile.hostname)
                benchmarkResult?.let { BenchmarkChip(result = it, isFastest = isFastest) }
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.profile_delete, profile.displayName()),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Penyusun hostname NextDNS dari Config ID + nama perangkat opsional. */
@Composable
fun NextDnsHelper(onUseHostname: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var configInput by rememberSaveable { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf("") }

    val result = remember(configInput, deviceName) {
        if (configInput.isBlank()) null else NextDnsHostname.build(configInput, deviceName)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.nextdns_toggle), fontSize = 12.sp)
        }

        if (expanded) {
            OutlinedTextField(
                value = configInput,
                onValueChange = { configInput = it },
                label = { Text(stringResource(R.string.nextdns_config_label), fontSize = 12.sp) },
                placeholder = { Text(stringResource(R.string.nextdns_config_placeholder), fontSize = 12.sp) },
                singleLine = true,
                isError = result is NextDnsHostname.Result.Invalid,
                supportingText = {
                    when (result) {
                        is NextDnsHostname.Result.Invalid -> Text(result.error.toUiText().resolve(), color = ErrorRed, fontSize = 12.sp)
                        else -> Text(stringResource(R.string.nextdns_config_hint), fontSize = 12.sp)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text(stringResource(R.string.nextdns_device_label), fontSize = 12.sp) },
                placeholder = { Text(stringResource(R.string.nextdns_device_placeholder), fontSize = 12.sp) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.nextdns_device_hint), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            if (result is NextDnsHostname.Result.Valid) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = result.hostname,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = PrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onUseHostname(result.hostname) }) {
                        Text(stringResource(R.string.nextdns_use), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Form nama profil + tombol simpan untuk hostname kustom yang valid. */
@Composable
fun SaveProfileRow(onSave: (name: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.profile_name_label), fontSize = 12.sp) },
            placeholder = { Text(stringResource(R.string.profile_name_placeholder), fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = {
                onSave(name)
                name = ""
            },
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(stringResource(R.string.profile_save), fontSize = 12.sp)
        }
    }
}
