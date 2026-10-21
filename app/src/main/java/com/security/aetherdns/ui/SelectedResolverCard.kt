package com.security.aetherdns.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.security.aetherdns.R
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.util.InspectionResult

/** Ringkasan resolver terpilih di Beranda, dengan pintasan ke tab Resolver. */
@Composable
fun SelectedResolverCard(name: String, hostname: String, benchmarkResult: InspectionResult?, onChange: () -> Unit) {
    Card(
        onClick = onChange,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.small, color = PrimaryBlue.copy(alpha = 0.12f)) {
                Icon(Icons.Default.Dns, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_resolver_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (hostname.isNotBlank()) HostnameText(hostname)
                benchmarkResult?.let { BenchmarkChip(result = it, isFastest = false) }
            }
            FilledTonalButton(onClick = onChange) { Text(stringResource(R.string.home_change_resolver)) }
        }
    }
}
