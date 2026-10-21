package com.security.aetherdns.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.aetherdns.R
import com.security.aetherdns.ui.theme.ErrorContainer
import com.security.aetherdns.ui.theme.ErrorRed
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessContainer
import com.security.aetherdns.ui.theme.SuccessGreen
import com.security.aetherdns.ui.theme.WarningOrange
import com.security.aetherdns.ui.theme.WarningOrangeLight
import com.security.aetherdns.util.CheckResult
import com.security.aetherdns.util.NetworkCheck
import com.security.aetherdns.util.NetworkReport

/**
 * "Cek jaringan": apakah ISP membelokkan DNS biasa dan apakah DoT bisa dipakai. Bila DoT diblokir saat proteksi
 * aktif, menawarkan jeda satu ketuk agar internet tidak putus.
 */
@Composable
fun NetworkCheckCard(
    report: NetworkReport?,
    isChecking: Boolean,
    isProtectionActive: Boolean,
    onRun: () -> Unit,
    onPause: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Router, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.netcheck_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = onRun,
                    enabled = !isChecking,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.common_testing), fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.netcheck_run), fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.netcheck_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = report != null) {
                report?.let { NetworkReportContent(it, isProtectionActive, onPause) }
            }
        }
    }
}

@Composable
private fun NetworkReportContent(report: NetworkReport, isProtectionActive: Boolean, onPause: () -> Unit) {
    val dotBlocked = report[NetworkCheck.DOT_REACHABLE] == CheckResult.PROBLEM
    val (summary, color, container) = when {
        dotBlocked && isProtectionActive -> Triple(R.string.netcheck_summary_dot_blocked, ErrorRed, ErrorContainer)
        report.isPlainDnsTampered && isProtectionActive ->
            Triple(R.string.netcheck_summary_protected, SuccessGreen, SuccessContainer)
        report.isPlainDnsTampered -> Triple(R.string.netcheck_summary_exposed, WarningOrange, WarningOrangeLight)
        else -> Triple(R.string.netcheck_summary_clean, SuccessGreen, SuccessContainer)
    }

    Column {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(shape = MaterialTheme.shapes.small, color = container, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
                if (dotBlocked && isProtectionActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onPause,
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_pause_15), fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        CheckRow(
            report[NetworkCheck.INTERCEPTION],
            ok = R.string.netcheck_interception_ok,
            problem = R.string.netcheck_interception_problem,
            unknown = R.string.netcheck_hijack_unknown
        )
        CheckRow(
            report[NetworkCheck.NXDOMAIN_HIJACK],
            ok = R.string.netcheck_hijack_ok,
            problem = R.string.netcheck_hijack_problem,
            unknown = R.string.netcheck_hijack_unknown
        )
        CheckRow(
            report[NetworkCheck.DOT_REACHABLE],
            ok = R.string.netcheck_dot_ok,
            problem = R.string.netcheck_dot_problem,
            unknown = R.string.netcheck_dot_unknown
        )
    }
}

@Composable
private fun CheckRow(result: CheckResult, ok: Int, problem: Int, unknown: Int) {
    val (icon: ImageVector, color: Color, text: Int) = when (result) {
        CheckResult.OK -> Triple(Icons.Default.CheckCircle, SuccessGreen, ok)
        CheckResult.PROBLEM -> Triple(Icons.Default.Error, ErrorRed, problem)
        CheckResult.INCONCLUSIVE -> Triple(Icons.AutoMirrored.Filled.HelpOutline, MaterialTheme.colorScheme.onSurfaceVariant, unknown)
    }
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Dialog dari cek otomatis: jaringan baru memblokir DoT saat proteksi aktif, sehingga internet bisa putus. */
@Composable
fun DotBlockedDialog(onPause: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed) },
        title = { Text(stringResource(R.string.netcheck_dialog_title)) },
        text = { Text(stringResource(R.string.netcheck_summary_dot_blocked)) },
        confirmButton = {
            Button(onClick = onPause) { Text(stringResource(R.string.action_pause_15)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.netcheck_dialog_keep)) }
        }
    )
}
