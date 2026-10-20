package com.security.aetherdns.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.aetherdns.model.DnsProfile
import com.security.aetherdns.ui.theme.ErrorRed
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.NextDnsHostname

/** Kartu profil kustom tersimpan, dengan tombol hapus. */
@Composable
fun SavedProfileCard(
    profile: DnsProfile,
    isSelected: Boolean,
    benchmarkResult: DotProbeResult?,
    isFastest: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PrimaryBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(1.5.dp, PrimaryBlue) else null
    ) {
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
                    Text(profile.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Text(
                    text = profile.hostname,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = PrimaryBlue,
                    modifier = Modifier.padding(top = 4.dp)
                )
                benchmarkResult?.let { BenchmarkChip(result = it, isFastest = isFastest) }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus profil ${profile.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Pakai NextDNS? Susun hostname dari Config ID", fontSize = 12.sp)
        }

        if (expanded) {
            OutlinedTextField(
                value = configInput,
                onValueChange = { configInput = it },
                label = { Text("Config ID / URL NextDNS", fontSize = 12.sp) },
                placeholder = { Text("abc123 atau https://dns.nextdns.io/abc123", fontSize = 12.sp) },
                singleLine = true,
                isError = result is NextDnsHostname.Result.Invalid,
                supportingText = {
                    when (result) {
                        is NextDnsHostname.Result.Invalid -> Text(result.reason, color = ErrorRed, fontSize = 11.sp)
                        else -> Text("Lihat di my.nextdns.io → Setup → Endpoints", fontSize = 11.sp)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Nama perangkat (opsional)", fontSize = 12.sp) },
                placeholder = { Text("mis. Pixel 8", fontSize = 12.sp) },
                singleLine = true,
                supportingText = { Text("Agar perangkat ini terpisah di log & analytics NextDNS", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            if (result is NextDnsHostname.Result.Valid) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = result.hostname,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = PrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onUseHostname(result.hostname) }) {
                        Text("Gunakan", fontSize = 12.sp)
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
            label = { Text("Nama profil (opsional)", fontSize = 12.sp) },
            placeholder = { Text("mis. Rumah – Pi-hole", fontSize = 12.sp) },
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
            Text("Simpan", fontSize = 12.sp)
        }
    }
}
