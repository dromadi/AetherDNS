package com.security.aetherdns.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.security.aetherdns.R
import com.security.aetherdns.data.NextDnsSummary
import com.security.aetherdns.ui.theme.ErrorRed
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessGreen
import java.text.NumberFormat

private const val NEXTDNS_ACCOUNT_URL = "https://my.nextdns.io/account"

/**
 * Bukti manfaat: jumlah kueri yang diblokir NextDNS dalam 24 jam terakhir. Hanya tampil bila resolver aktif
 * adalah profil NextDNS pribadi; tanpa API key menampilkan langkah penyiapan.
 */
@Composable
fun BlockStatsCard(
    hasApiKey: Boolean,
    state: StatsUiState,
    onSaveKey: (String) -> Unit,
    onRemoveKey: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.stats_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (hasApiKey) {
                        Text(
                            stringResource(R.string.stats_period),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (hasApiKey) {
                    IconButton(onClick = onRefresh, enabled = state !is StatsUiState.Loading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.stats_refresh))
                    }
                    KeyMenu(onRemoveKey)
                }
            }

            if (!hasApiKey) {
                KeySetup(onSaveKey)
            } else {
                when (state) {
                    StatsUiState.Idle, StatsUiState.Loading -> LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                    is StatsUiState.Loaded -> StatsContent(state.summary)
                    is StatsUiState.Error -> Text(
                        state.error.toUiText().resolve(),
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsContent(summary: NextDnsSummary) {
    val format = remember { NumberFormat.getIntegerInstance() }
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        stringResource(R.string.stats_blocked_count, format.format(summary.blockedQueries)),
        style = MaterialTheme.typography.headlineMedium,
        color = SuccessGreen
    )
    Text(
        stringResource(R.string.stats_blocked_detail, summary.blockedPercent, format.format(summary.totalQueries)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LinearProgressIndicator(
        progress = { summary.blockedPercent / 100f },
        color = SuccessGreen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )

    if (summary.topBlocked.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.stats_top_blocked), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        summary.topBlocked.forEach { (domain, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.Block, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    domain,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(format.format(count), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun KeySetup(onSave: (String) -> Unit) {
    val context = LocalContext.current
    var key by rememberSaveable { mutableStateOf("") }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        stringResource(R.string.stats_setup_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    TextButton(onClick = {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, NEXTDNS_ACCOUNT_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.stats_get_key))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim() },
            label = { Text(stringResource(R.string.stats_key_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.weight(1f)
        )
        Button(onClick = { onSave(key) }, enabled = key.isNotBlank()) {
            Text(stringResource(R.string.stats_save))
        }
    }
}

@Composable
private fun KeyMenu(onRemoveKey: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats_remove_key)) },
                onClick = {
                    expanded = false
                    onRemoveKey()
                }
            )
        }
    }
}
