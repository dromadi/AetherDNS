package com.security.aetherdns.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.aetherdns.R
import com.security.aetherdns.automation.AutomationProtocol
import com.security.aetherdns.automation.AutomationState
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.WarningOrange

/** Pengaturan integrasi Tasker / MacroDroid: sakelar, token rahasia, dan contoh konfigurasi intent. */
@Composable
fun AutomationCard(state: AutomationState, onToggle: (Boolean) -> Unit, onRegenerateToken: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.automation_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(stringResource(R.string.automation_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.enabled, onCheckedChange = onToggle)
            }

            if (state.enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.automation_token_label), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.token,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = PrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        copy(context, state.token)
                        Toast.makeText(context, R.string.automation_token_copied, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.perm_copy), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onRegenerateToken) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.automation_regenerate), modifier = Modifier.size(18.dp))
                    }
                }
                Text(stringResource(R.string.automation_token_warning), fontSize = 11.sp, color = WarningOrange)

                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.automation_howto_title), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            R.string.automation_howto_body,
                            context.packageName,
                            AutomationProtocol.ACTION_ENABLE,
                            AutomationProtocol.ACTION_DISABLE,
                            AutomationProtocol.ACTION_PAUSE,
                            AutomationProtocol.ACTION_APPLY,
                            state.token
                        ),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("AetherDNS token", text))
}
