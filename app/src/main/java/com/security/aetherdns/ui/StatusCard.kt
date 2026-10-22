package com.security.aetherdns.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon as GraphicsIcon
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.aetherdns.R
import com.security.aetherdns.data.DnsState
import com.security.aetherdns.service.AetherDnsTileService
import com.security.aetherdns.ui.theme.OnBrandDanger
import com.security.aetherdns.ui.theme.WarningOrange
import java.text.DateFormat
import java.util.Date

/** Kartu utama: status proteksi beranimasi, validasi OS, tombol aktif/nonaktif, jeda, dan pintasan tile. */
@Composable
fun StatusCard(
    state: DnsState,
    resolverName: String?,
    isProbing: Boolean,
    onChangeResolver: () -> Unit,
    onToggle: () -> Unit,
    onPause: () -> Unit,
    /** Tindakan Autopilot yang sedang berlaku (dijeda / resolver pengganti), null bila tidak ada. */
    autopilotNote: String? = null
) {
    val active = state.isProtectionActive
    val haptics = LocalHapticFeedback.current
    // Saat aktif kartu memakai gradien brand (seperti ikon aplikasi) dengan konten putih
    val gradientAlpha by animateFloatAsState(if (active) 1f else 0f, animationSpec = tween(500), label = "statusGradient")
    val onCard = if (active) Color.White else MaterialTheme.colorScheme.onSurface
    val onCardMuted = if (active) Color.White.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant
    val accent by animateColorAsState(
        targetValue = when {
            active -> Color.White
            state.pausedUntil != null -> WarningOrange
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(400),
        label = "statusAccent"
    )
    // Denyut halus selama OS memvalidasi TLS atau probe berjalan, agar jelas ada proses
    val pulsing = isProbing || (active && !state.isSystemDoTValidated)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
      Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .alpha(gradientAlpha)
                .brandGradient()
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ShieldBadge(active = active, accent = accent, pulsing = pulsing)

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = active,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                label = "statusTitle"
            ) { isActive ->
                Text(
                    text = stringResource(if (isActive) R.string.status_active_title else R.string.status_inactive_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = onCard,
                    textAlign = TextAlign.Center
                )
            }

            if (active) {
                if (resolverName != null) {
                    Text(
                        text = resolverName,
                        style = MaterialTheme.typography.titleMedium,
                        color = onCard,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    text = state.specifier,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = onCardMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                Text(
                    text = stringResource(R.string.status_inactive_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (resolverName != null) {
                    Text(
                        text = stringResource(R.string.status_selected_resolver, resolverName),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            if (autopilotNote != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) Color.White.copy(alpha = 0.18f) else WarningOrange.copy(alpha = 0.14f),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoMode,
                            contentDescription = null,
                            tint = if (active) Color.White else WarningOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            autopilotNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            TextButton(
                onClick = onChangeResolver,
                colors = ButtonDefaults.textButtonColors(contentColor = if (active) Color.White else MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_change_resolver))
            }

            // Indikator jeda sementara (captive portal)
            state.pausedUntil?.takeIf { !active }?.let { until ->
                StatusPill(
                    icon = Icons.Default.PauseCircle,
                    color = WarningOrange,
                    text = stringResource(R.string.status_paused, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(until)))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tombol utama dengan pre-flight DoT probe
            val buttonColors = if (active) {
                ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = OnBrandDanger)
            } else {
                ButtonDefaults.buttonColors()
            }
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
                enabled = !isProbing,
                colors = buttonColors,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isProbing) {
                    CircularProgressIndicator(color = LocalContentColor.current, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.action_testing_dot), fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(
                        imageVector = when {
                            !state.hasPermission -> Icons.Default.Key
                            active -> Icons.Default.PowerSettingsNew
                            else -> Icons.Default.Bolt
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(
                            when {
                                !state.hasPermission -> R.string.action_grant_first
                                active -> R.string.action_disable
                                state.pausedUntil != null -> R.string.action_resume
                                else -> R.string.action_enable
                            }
                        ),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }

            // Jeda sementara untuk login Wi-Fi captive portal (hotel, bandara, kafe)
            AnimatedVisibility(visible = active && state.hasPermission) {
                OutlinedButton(
                    onClick = onPause,
                    enabled = !isProbing,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = onCard),
                    border = BorderStroke(1.dp, onCard.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_pause_15), fontSize = 13.sp)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(4.dp))
                AddTileButton(contentColor = if (active) Color.White else MaterialTheme.colorScheme.primary)
            }
        }
      }
    }
}

/** Ikon perisai dengan cincin berdenyut saat [pulsing]. */
@Composable
private fun ShieldBadge(active: Boolean, accent: Color, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "shieldPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(if (pulsing) pulse else 1f)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f))
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            // Saat aktif memakai lambang aplikasi (perisai + gelombang), sama seperti ikon launcher & tile
            Icon(
                painter = if (active) painterResource(R.drawable.ic_tile_shield) else rememberVectorPainter(Icons.Default.GppBad),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(44.dp)
            )
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
            Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

/** Pintasan menambahkan Quick Settings Tile (Android 13+). */
@Composable
internal fun AddTileButton(contentColor: Color = MaterialTheme.colorScheme.primary) {
    val context = LocalContext.current
    TextButton(
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        onClick = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@TextButton
            try {
                context.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                    ComponentName(context, AetherDnsTileService::class.java),
                    context.getString(R.string.tile_label),
                    GraphicsIcon.createWithResource(context, R.drawable.ic_tile_shield),
                    context.mainExecutor
                ) { result ->
                    // Sebelumnya hasil diabaikan: bila tile sudah terpasang atau launcher (mis. One UI) menolak,
                    // tombol terlihat tidak melakukan apa-apa
                    TileRequestResult.messageFor(result)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                Toast.makeText(context, R.string.toast_tile_manual, Toast.LENGTH_LONG).show()
            }
        }
    ) {
        Icon(imageVector = Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.action_add_tile), fontSize = 12.sp)
    }
}

/**
 * Pesan untuk hasil `StatusBarManager.requestAddTileService` (nilai konstanta API 33 ditulis langsung agar bisa
 * diuji di JVM). `null` = tidak perlu pesan (pengguna sendiri menekan "Jangan tambahkan").
 */
internal object TileRequestResult {
    private const val NOT_ADDED = 0
    private const val ALREADY_ADDED = 1
    private const val ADDED = 2

    fun messageFor(result: Int): Int? = when (result) {
        ADDED -> R.string.toast_tile_added
        ALREADY_ADDED -> R.string.toast_tile_already_added
        NOT_ADDED -> null
        // 1000+ = galat (aplikasi tidak di depan, permintaan berjalan, tidak didukung launcher, dsb.)
        else -> R.string.toast_tile_manual
    }
}
