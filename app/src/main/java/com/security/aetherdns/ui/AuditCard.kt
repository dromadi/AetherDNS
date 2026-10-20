package com.security.aetherdns.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.aetherdns.R
import com.security.aetherdns.ui.theme.ErrorRed
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessGreen
import com.security.aetherdns.util.AuditItem
import com.security.aetherdns.util.AuditOutcome
import com.security.aetherdns.util.AuditSummary

/** Audit sinkhole real-time melalui DNS sistem yang sedang aktif. */
@Composable
fun AuditCard(summary: AuditSummary?, isAuditing: Boolean, onRunAudit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.audit_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                FilledTonalButton(
                    onClick = onRunAudit,
                    enabled = !isAuditing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isAuditing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.common_testing), fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.audit_run), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.audit_description),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            summary?.let { AuditResult(it) }
        }
    }
}

@Composable
private fun AuditResult(summary: AuditSummary) {
    val ok = summary.isNetworkReachable && summary.isOverallProtected
    val color = if (ok) SuccessGreen else ErrorRed

    Spacer(modifier = Modifier.height(12.dp))
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                val (title, description) = when {
                    !summary.isNetworkReachable ->
                        stringResource(R.string.audit_offline_title) to stringResource(R.string.audit_offline_desc)
                    summary.isOverallProtected ->
                        stringResource(R.string.audit_result_safe, summary.blockedCount, summary.totalCount) to
                            stringResource(R.string.audit_desc_safe, summary.blockedCount, summary.totalCount)
                    else ->
                        stringResource(R.string.audit_result_leak, summary.blockedCount, summary.totalCount) to
                            stringResource(R.string.audit_desc_leak, summary.blockedCount, summary.totalCount)
                }
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
                Text(text = description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    summary.items.forEach { AuditRow(it) }
}

@Composable
private fun AuditRow(item: AuditItem) {
    val color = if (item.isBlocked) SuccessGreen else ErrorRed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(item.domain, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f)) {
            Text(
                text = when (item.outcome) {
                    AuditOutcome.SINKHOLE -> stringResource(R.string.audit_outcome_sinkhole, item.detail)
                    AuditOutcome.NXDOMAIN -> stringResource(R.string.audit_outcome_nxdomain)
                    AuditOutcome.RESOLVED -> item.detail
                    AuditOutcome.ERROR -> stringResource(R.string.audit_outcome_error, item.detail)
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
