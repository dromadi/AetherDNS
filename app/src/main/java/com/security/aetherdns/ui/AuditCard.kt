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
import com.security.aetherdns.ui.theme.WarningOrange
import com.security.aetherdns.util.AuditItem
import com.security.aetherdns.util.AuditOutcome
import com.security.aetherdns.util.AuditSummary

/** Hasil audit per domain, ditampilkan di bagian "Detail teknis" kartu Kesehatan proteksi. */
@Composable
internal fun AuditDetailRows(summary: AuditSummary) {
    if (!summary.isNetworkReachable) {
        Text(
            stringResource(R.string.audit_offline_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    if (summary.cachedCount > 0) {
        Text(
            text = stringResource(R.string.audit_cached_note, summary.cachedCount),
            fontSize = 12.sp,
            color = WarningOrange,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
    summary.items.forEach { AuditRow(it) }
}

@Composable
private fun AuditRow(item: AuditItem) {
    val color = when (item.outcome) {
        AuditOutcome.CACHED -> WarningOrange
        else -> if (item.isBlocked) SuccessGreen else ErrorRed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp)) {
            Text(item.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(item.domain, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Lebar chip dibatasi: daftar IP panjang tidak boleh menjepit kolom nama domain
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.widthIn(max = 170.dp)
        ) {
            Text(
                text = when (item.outcome) {
                    AuditOutcome.SINKHOLE -> stringResource(R.string.audit_outcome_sinkhole, item.detail)
                    AuditOutcome.NXDOMAIN -> stringResource(R.string.audit_outcome_nxdomain)
                    AuditOutcome.RESOLVED -> firstAddress(item.detail)
                    AuditOutcome.CACHED -> stringResource(R.string.audit_outcome_cached)
                    AuditOutcome.ERROR -> stringResource(R.string.audit_outcome_error, item.detail)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/** "a, b, c" → "a +2" agar chip tetap ringkas. */
private fun firstAddress(joined: String): String {
    val parts = joined.split(", ").filter { it.isNotBlank() }
    return when {
        parts.size <= 1 -> joined
        else -> "${parts.first()} +${parts.size - 1}"
    }
}
