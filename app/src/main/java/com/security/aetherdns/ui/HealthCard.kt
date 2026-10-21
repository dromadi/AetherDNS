package com.security.aetherdns.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.security.aetherdns.R
import com.security.aetherdns.ui.theme.ErrorRed
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessGreen
import com.security.aetherdns.ui.theme.WarningOrange
import com.security.aetherdns.util.AuditSummary
import com.security.aetherdns.util.NetworkReport

/**
 * Satu kartu ringkas pengganti Cek jaringan + Audit: beberapa baris berbahasa awam, detail teknis disembunyikan.
 */
@Composable
fun HealthCard(
    summary: HealthSummary,
    isChecking: Boolean,
    report: NetworkReport?,
    audit: AuditSummary?,
    onRefresh: () -> Unit,
    onPause: () -> Unit,
    onOpenInsights: () -> Unit
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.health_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(headline(summary.level, isChecking)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = levelColor(summary.level)
                    )
                }
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.health_refresh))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            summary.rows.forEach { row -> HealthRowView(row) }

            if (summary.suggestPause) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onPause,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_pause_15))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (report != null || audit != null) {
                    TextButton(onClick = { showDetails = !showDetails }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(if (showDetails) R.string.health_hide_details else R.string.health_details))
                        Icon(if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenInsights, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.health_open_insights))
                }
            }
            if (report != null || audit != null) {
                AnimatedVisibility(visible = showDetails) {
                    Column {
                        report?.let { NetworkCheckRows(it) }
                        if (audit != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            AuditDetailRows(audit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthRowView(row: HealthRow) {
    val (icon, color) = levelIcon(row.level)
    Row(modifier = Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            stringResource(row.text, *row.args.toTypedArray()),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun headline(level: HealthLevel, isChecking: Boolean): Int = when {
    isChecking && level == HealthLevel.PENDING -> R.string.health_headline_pending
    else -> when (level) {
        HealthLevel.GOOD -> R.string.health_headline_good
        HealthLevel.INFO -> R.string.health_headline_info
        HealthLevel.PENDING -> R.string.health_headline_pending
        HealthLevel.WARNING -> R.string.health_headline_warning
        HealthLevel.BAD -> R.string.health_headline_bad
    }
}

@Composable
private fun levelColor(level: HealthLevel): Color = when (level) {
    HealthLevel.GOOD, HealthLevel.INFO -> SuccessGreen
    HealthLevel.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    HealthLevel.WARNING -> WarningOrange
    HealthLevel.BAD -> ErrorRed
}

@Composable
private fun levelIcon(level: HealthLevel): Pair<ImageVector, Color> = when (level) {
    HealthLevel.GOOD -> Icons.Default.CheckCircle to SuccessGreen
    HealthLevel.INFO -> Icons.Default.Info to PrimaryBlue
    HealthLevel.PENDING -> Icons.Default.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
    HealthLevel.WARNING -> Icons.Default.Warning to WarningOrange
    HealthLevel.BAD -> Icons.Default.Error to ErrorRed
}

