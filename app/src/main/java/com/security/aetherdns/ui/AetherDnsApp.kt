package com.security.aetherdns.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.model.DnsProfile
import com.security.aetherdns.ui.theme.*
import com.security.aetherdns.util.AuditSummary
import com.security.aetherdns.util.DnsAuditor
import com.security.aetherdns.util.DnsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherDnsApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // State management
    var hasPermission by remember { mutableStateOf(DnsManager.hasSecurePermission(context)) }
    var currentMode by remember { mutableStateOf(DnsManager.getPrivateDnsMode(context)) }
    var currentSpecifier by remember { mutableStateOf(DnsManager.getPrivateDnsSpecifier(context)) }
    var selectedProfileId by remember {
        val matched = DefaultProfiles.list.find { it.hostname.equals(currentSpecifier, ignoreCase = true) }
        mutableStateOf(matched?.id ?: if (currentSpecifier.isNotBlank()) "custom" else "adguard_default")
    }
    var customHostname by remember {
        mutableStateOf(if (selectedProfileId == "custom") currentSpecifier else "")
    }

    // Live Audit State
    var isAuditing by remember { mutableStateOf(false) }
    var auditSummary by remember { mutableStateOf<AuditSummary?>(null) }

    fun refreshState() {
        hasPermission = DnsManager.hasSecurePermission(context)
        currentMode = DnsManager.getPrivateDnsMode(context)
        currentSpecifier = DnsManager.getPrivateDnsSpecifier(context)
    }

    val isProtectionActive = currentMode == DnsManager.MODE_HOSTNAME && currentSpecifier.isNotBlank()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AetherDNS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Native DoT & Ad-Sinkhole Controller",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasPermission) SuccessGreen.copy(alpha = 0.15f) else WarningOrange.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (hasPermission) SuccessGreen else WarningOrange)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasPermission) "Mode Native Aktif" else "Perlu Izin ADB",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasPermission) SuccessGreen else WarningOrange
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Status Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isProtectionActive) PrimaryBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
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
                                    if (isProtectionActive) SuccessGreen.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isProtectionActive) Icons.Default.Shield else Icons.Default.GppBad,
                                contentDescription = null,
                                tint = if (isProtectionActive) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isProtectionActive) "Proteksi DNS Aktif" else "Proteksi Nonaktif",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (isProtectionActive) SuccessGreen else MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = if (isProtectionActive) currentSpecifier else "Sistem menggunakan DNS default bawaan ISP",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Big Toggle Button
                        Button(
                            onClick = {
                                if (!hasPermission) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Izin WRITE_SECURE_SETTINGS diperlukan untuk switch otomatis.")
                                    }
                                    return@Button
                                }

                                if (isProtectionActive) {
                                    val result = DnsManager.disablePrivateDns(context)
                                    if (result.isSuccess) {
                                        refreshState()
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Proteksi DNS dinonaktifkan")
                                        }
                                    }
                                } else {
                                    val targetHostname = if (selectedProfileId == "custom") {
                                        customHostname.trim()
                                    } else {
                                        DefaultProfiles.list.find { it.id == selectedProfileId }?.hostname ?: "dns.adguard-dns.com"
                                    }

                                    if (targetHostname.isBlank()) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Masukkan hostname DNS terlebih dahulu")
                                        }
                                        return@Button
                                    }

                                    val result = DnsManager.applyPrivateDns(context, targetHostname)
                                    if (result.isSuccess) {
                                        refreshState()
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Proteksi DNS berhasil diaktifkan ke $targetHostname")
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Gagal: ${result.exceptionOrNull()?.message}")
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isProtectionActive) ErrorRed else PrimaryBlue
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isProtectionActive) Icons.Default.PowerSettingsNew else Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isProtectionActive) "Matikan Proteksi" else "Aktifkan Proteksi Sekarang",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 2. Live Diagnostic / Audit Section
            item {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NetworkCheck,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Audit Real-Time Sinkhole Iklan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    isAuditing = true
                                    coroutineScope.launch {
                                        auditSummary = DnsAuditor.runAudit()
                                        isAuditing = false
                                    }
                                },
                                enabled = !isAuditing,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (isAuditing) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Menguji...", fontSize = 12.sp)
                                } else {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Uji DNS", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Menguji resolusi domain AdMob, DoubleClick, dan AppLovin untuk memastikan status sinkhole aktif.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        auditSummary?.let { summary ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (summary.isOverallProtected) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (summary.isOverallProtected) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (summary.isOverallProtected) SuccessGreen else ErrorRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (summary.isOverallProtected)
                                                "Hasil: ${summary.blockedCount}/${summary.totalCount} Domain Terfilter (Aman)"
                                            else
                                                "Hasil: ${summary.blockedCount}/${summary.totalCount} Domain Terfilter (Bocor)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (summary.isOverallProtected) SuccessGreen else ErrorRed
                                        )
                                        Text(
                                            text = if (summary.isOverallProtected)
                                                "Iklan pihak ketiga berhasil di-sinkhole ke NXDOMAIN / 0.0.0.0"
                                            else
                                                "Koneksi iklan masih berhasil tembus. Pastikan Private DNS aktif.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            summary.items.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.description, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text(item.domain, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (item.isBlocked) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = item.resolvedIp,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (item.isBlocked) SuccessGreen else ErrorRed,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Setup Assistant / Guide Card (If permission is NOT granted)
            if (!hasPermission) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningOrangeLight)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = WarningOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Aktivasi 1-Klik Tanpa Root (ADB)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = WarningOrange
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Agar aplikasi dapat mengontrol DNS bawaan Android langsung (0% konsumsi baterai & RAM), berikan izin WRITE_SECURE_SETTINGS 1 kali melalui ADB di PC atau Shizuku/Termux:",
                                fontSize = 12.sp,
                                color = Color(0xFF424242)
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            val adbCommand = "adb shell pm grant com.security.aetherdns android.permission.WRITE_SECURE_SETTINGS"

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF212121),
                                modifier = Modifier.fillMaxWidth()
                            ) {
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
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", adbCommand))
                                            Toast.makeText(context, "Perintah ADB berhasil disalin!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Salin",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { refreshState() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cek Ulang Izin", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        val targetHostname = if (selectedProfileId == "custom") customHostname.trim()
                                        else DefaultProfiles.list.find { it.id == selectedProfileId }?.hostname ?: "dns.adguard-dns.com"

                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("DNS Hostname", targetHostname))
                                        Toast.makeText(context, "Hostname '$targetHostname' disalin ke clipboard!", Toast.LENGTH_SHORT).show()

                                        DnsManager.openSystemDnsSettings(context)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WarningOrange)
                                ) {
                                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Buka DNS HP", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 4. DNS Preset Profiles Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pilih Preset DNS Filtering",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            items(DefaultProfiles.list) { profile ->
                val isSelected = selectedProfileId == profile.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedProfileId = profile.id
                            if (hasPermission && isProtectionActive) {
                                DnsManager.applyPrivateDns(context, profile.hostname)
                                refreshState()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Beralih ke ${profile.name}")
                                }
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) PrimaryBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlue) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedProfileId = profile.id
                                if (hasPermission && isProtectionActive) {
                                    DnsManager.applyPrivateDns(context, profile.hostname)
                                    refreshState()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Beralih ke ${profile.name}")
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = profile.category,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = profile.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = profile.hostname,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // 5. Custom Hostname Option
            item {
                val isCustomSelected = selectedProfileId == "custom"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedProfileId = "custom" },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCustomSelected) PrimaryBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isCustomSelected) androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlue) else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isCustomSelected,
                                onClick = { selectedProfileId = "custom" }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Hostname Kustom (NextDNS / VPS Sendiri)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Gunakan ID NextDNS pribadi atau instance AdGuard Home / Pi-hole sendiri.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (isCustomSelected) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = customHostname,
                                onValueChange = { customHostname = it },
                                label = { Text("Hostname DoT (contoh: xxxxxx.dns.nextdns.io)", fontSize = 12.sp) },
                                placeholder = { Text("dns.domainkamu.com", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (hasPermission && isProtectionActive && customHostname.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        DnsManager.applyPrivateDns(context, customHostname.trim())
                                        refreshState()
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Hostname kustom diterapkan: $customHostname")
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Terapkan", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
