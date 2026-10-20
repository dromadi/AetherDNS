package com.security.aetherdns.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon as GraphicsIcon
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.aetherdns.R
import com.security.aetherdns.data.DnsState
import com.security.aetherdns.service.AetherDnsTileService
import com.security.aetherdns.ui.theme.ErrorRed
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessGreen
import com.security.aetherdns.ui.theme.WarningOrange
import java.text.DateFormat
import java.util.Date

/** Kartu utama: status proteksi, validasi OS, tombol aktif/nonaktif, jeda, dan pintasan tile. */
@Composable
fun StatusCard(
    state: DnsState,
    isProbing: Boolean,
    onToggle: () -> Unit,
    onPause: () -> Unit
) {
    val active = state.isProtectionActive
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) PrimaryBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) SuccessGreen.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (active) Icons.Default.Shield else Icons.Default.GppBad,
                    contentDescription = null,
                    tint = if (active) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(if (active) R.string.status_active_title else R.string.status_inactive_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (active) SuccessGreen else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (active) state.specifier else stringResource(R.string.status_inactive_subtitle),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Indikator jeda sementara (captive portal)
            state.pausedUntil?.takeIf { !active }?.let { until ->
                StatusPill(
                    icon = Icons.Default.PauseCircle,
                    color = WarningOrange,
                    text = stringResource(R.string.status_paused, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(until)))
                )
            }

            // Indikator validasi DoT oleh OS (LinkProperties API 28+)
            if (active) {
                if (state.isSystemDoTValidated) {
                    StatusPill(
                        icon = Icons.Default.CheckCircle,
                        color = SuccessGreen,
                        text = stringResource(R.string.status_os_validated, state.validatedServerName ?: state.specifier)
                    )
                } else {
                    StatusPill(icon = Icons.Default.Sync, color = WarningOrange, text = stringResource(R.string.status_os_validating))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tombol utama dengan pre-flight DoT probe
            Button(
                onClick = onToggle,
                enabled = !isProbing,
                colors = ButtonDefaults.buttonColors(containerColor = if (active) ErrorRed else PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isProbing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_testing_dot), fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(
                        imageVector = if (active) Icons.Default.PowerSettingsNew else Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            when {
                                active -> R.string.action_disable
                                state.pausedUntil != null -> R.string.action_resume
                                else -> R.string.action_enable
                            }
                        ),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Jeda sementara untuk login Wi-Fi captive portal (hotel, bandara, kafe)
            if (active && state.hasPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onPause,
                    enabled = !isProbing,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_pause_15), fontSize = 13.sp)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(8.dp))
                AddTileButton()
            }
        }
    }
}

@Composable
private fun StatusPill(icon: ImageVector, color: Color, text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

/** Pintasan menambahkan Quick Settings Tile (Android 13+). */
@Composable
private fun AddTileButton() {
    val context = LocalContext.current
    TextButton(
        onClick = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@TextButton
            try {
                context.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                    ComponentName(context, AetherDnsTileService::class.java),
                    context.getString(R.string.tile_label),
                    GraphicsIcon.createWithResource(context, R.drawable.ic_tile_shield),
                    context.mainExecutor
                ) { _ -> }
            } catch (e: Exception) {
                Toast.makeText(context, R.string.toast_tile_manual, Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Icon(imageVector = Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.action_add_tile), fontSize = 12.sp)
    }
}
