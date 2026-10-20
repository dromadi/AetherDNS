package com.security.aetherdns.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.security.aetherdns.shizuku.GranterStatus
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessGreen
import com.security.aetherdns.ui.theme.WarningOrange
import com.security.aetherdns.ui.theme.WarningOrangeLight
import com.security.aetherdns.util.DnsManager
import com.security.aetherdns.util.SystemShortcuts

/** Asisten izin WRITE_SECURE_SETTINGS: Shizuku (tanpa PC) dan perintah ADB manual. */
@Composable
fun PermissionAssistantCard(
    shizukuStatus: GranterStatus,
    isGranting: Boolean,
    selectedHostname: String,
    onGrant: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WarningOrangeLight)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ShizukuSection(shizukuStatus, isGranting, onGrant)

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = WarningOrange.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            AdbSection(selectedHostname, onRefresh)
        }
    }
}

@Composable
private fun ShizukuSection(status: GranterStatus, isGranting: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Bolt, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.perm_shizuku_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryBlue)
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(stringResource(R.string.perm_shizuku_desc), fontSize = 12.sp, color = Color(0xFF424242))
    Spacer(modifier = Modifier.height(10.dp))

    val (statusLabel, statusHint) = when (status) {
        GranterStatus.READY -> R.string.shizuku_status_ready to R.string.shizuku_hint_ready
        GranterStatus.NEEDS_AUTHORIZATION -> R.string.shizuku_status_needs_auth to R.string.shizuku_hint_needs_auth
        GranterStatus.NOT_RUNNING -> R.string.shizuku_status_not_running to R.string.shizuku_hint_not_running
        GranterStatus.NOT_INSTALLED -> R.string.shizuku_status_not_installed to R.string.shizuku_hint_not_installed
        GranterStatus.UNSUPPORTED -> R.string.shizuku_status_unsupported to R.string.shizuku_hint_unsupported
    }
    val (statusColor, statusBackground) = when (status) {
        GranterStatus.READY -> SuccessGreen to SuccessGreen.copy(alpha = 0.15f)
        GranterStatus.NEEDS_AUTHORIZATION -> WarningOrange to WarningOrange.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.perm_shizuku_status_label), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Surface(shape = RoundedCornerShape(6.dp), color = statusBackground) {
                    Text(
                        text = stringResource(statusLabel),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(stringResource(statusHint), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (status == GranterStatus.NOT_RUNNING || status == GranterStatus.NOT_INSTALLED) {
                ShizukuSetupShortcuts(isInstalled = status == GranterStatus.NOT_RUNNING)
            }

            if (status == GranterStatus.READY || status == GranterStatus.NEEDS_AUTHORIZATION) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onGrant,
                    enabled = !isGranting,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGranting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.perm_granting), fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(if (status == GranterStatus.READY) R.string.perm_grant_now else R.string.perm_authorize),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    Text(stringResource(R.string.perm_oem_hint), fontSize = 10.sp, color = Color(0xFF616161))
}

@Composable
private fun AdbSection(selectedHostname: String, onRefresh: () -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Terminal, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.perm_adb_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WarningOrange)
    }

    Spacer(modifier = Modifier.height(6.dp))
    val adbCommand = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF212121), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = adbCommand,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFF81C784),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    copyToClipboard(context, "ADB Command", adbCommand)
                    Toast.makeText(context, R.string.toast_adb_copied, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.perm_copy),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.perm_recheck), fontSize = 11.sp)
        }

        Button(
            onClick = {
                // Mode manual: salin hostname lalu buka pengaturan DNS bawaan HP untuk ditempel
                copyToClipboard(context, "DNS Hostname", selectedHostname)
                Toast.makeText(context, context.getString(R.string.toast_hostname_copied, selectedHostname), Toast.LENGTH_SHORT).show()
                DnsManager.openSystemDnsSettings(context)
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WarningOrange)
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.perm_open_dns_settings), fontSize = 11.sp)
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Pintasan menyiapkan Shizuku: buka Wireless debugging / Shizuku, plus langkah singkat. */
@Composable
private fun ShizukuSetupShortcuts(isInstalled: Boolean) {
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                if (SystemShortcuts.openWirelessDebugging(context) == SystemShortcuts.DeveloperTarget.ABOUT_PHONE) {
                    Toast.makeText(context, R.string.shizuku_enable_developer_options, Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.shizuku_open_wireless_debugging), fontSize = 11.sp)
        }
        OutlinedButton(
            onClick = { SystemShortcuts.openShizuku(context) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                stringResource(if (isInstalled) R.string.shizuku_open_app else R.string.shizuku_download),
                fontSize = 11.sp
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(stringResource(R.string.shizuku_setup_steps), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
