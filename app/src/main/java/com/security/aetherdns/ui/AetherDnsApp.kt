package com.security.aetherdns.ui

import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import com.security.aetherdns.BuildConfig
import com.security.aetherdns.model.DefaultProfiles
import com.security.aetherdns.service.AetherDnsTileService
import com.security.aetherdns.shizuku.GranterStatus
import com.security.aetherdns.ui.theme.*
import com.security.aetherdns.util.DnsManager
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.HostnameValidator
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherDnsApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val dnsState by viewModel.dnsState.collectAsState()
    val shizukuStatus by viewModel.shizukuStatus.collectAsState()
    val isProbing by viewModel.isProbing.collectAsState()
    val isGranting by viewModel.isGranting.collectAsState()
    val isAuditing by viewModel.isAuditing.collectAsState()
    val auditSummary by viewModel.auditSummary.collectAsState()
    val benchmark by viewModel.benchmark.collectAsState()

    // Handle snackbar events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    var selectedProfileId by remember(dnsState.specifier) {
        val matched = DefaultProfiles.list.find { it.hostname.equals(dnsState.specifier, ignoreCase = true) }
        mutableStateOf(matched?.id ?: if (dnsState.specifier.isNotBlank()) "custom" else "adguard_default")
    }

    var customHostname by remember(dnsState.specifier) {
        mutableStateOf(if (selectedProfileId == "custom") dnsState.specifier else "")
    }

    val customValidation = remember(customHostname) {
        if (customHostname.isBlank()) null else HostnameValidator.validate(customHostname)
    }
    val isCustomInvalid = customValidation is HostnameValidator.Result.Invalid
    val customErrorReason = (customValidation as? HostnameValidator.Result.Invalid)?.reason ?: ""

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
                            text = "Native DoT & Ad-Sinkhole Controller (v${BuildConfig.VERSION_NAME})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (dnsState.hasPermission) SuccessGreen.copy(alpha = 0.15f) else WarningOrange.copy(alpha = 0.15f),
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
                                    .background(if (dnsState.hasPermission) SuccessGreen else WarningOrange)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (dnsState.hasPermission) "Mode Native Aktif" else "Perlu Izin ADB",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (dnsState.hasPermission) SuccessGreen else WarningOrange
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
                        containerColor = if (dnsState.isProtectionActive) PrimaryBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
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
                                    if (dnsState.isProtectionActive) SuccessGreen.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (dnsState.isProtectionActive) Icons.Default.Shield else Icons.Default.GppBad,
                                contentDescription = null,
                                tint = if (dnsState.isProtectionActive) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (dnsState.isProtectionActive) "Proteksi DNS Aktif" else "Proteksi Nonaktif",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (dnsState.isProtectionActive) SuccessGreen else MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = if (dnsState.isProtectionActive) dnsState.specifier else "Sistem menggunakan DNS default bawaan ISP",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // Indikator Validasi Kernel OS (LinkProperties API 28+)
                        if (dnsState.isProtectionActive) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (dnsState.isSystemDoTValidated) SuccessGreen.copy(alpha = 0.12f) else WarningOrange.copy(alpha = 0.12f),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (dnsState.isSystemDoTValidated) Icons.Default.CheckCircle else Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = if (dnsState.isSystemDoTValidated) SuccessGreen else WarningOrange,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (dnsState.isSystemDoTValidated)
                                            "OS Kernel: DoT Terenkripsi (${dnsState.validatedServerName ?: dnsState.specifier})"
                                        else
                                            "OS Kernel: Sedang memvalidasi handshake TLS...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (dnsState.isSystemDoTValidated) SuccessGreen else WarningOrange
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Big Toggle Button with Pre-flight DoT Probe
                        Button(
                            onClick = {
                                if (!dnsState.hasPermission) {
                                    Toast.makeText(context, "Izin WRITE_SECURE_SETTINGS diperlukan", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                if (dnsState.isProtectionActive) {
                                    viewModel.disableDns()
                                } else {
                                    val targetHostname = if (selectedProfileId == "custom") {
                                        customHostname.trim()
                                    } else {
                                        DefaultProfiles.list.find { it.id == selectedProfileId }?.hostname ?: DnsManager.DEFAULT_FALLBACK_HOSTNAME
                                    }
                                    viewModel.applyDns(targetHostname, probe = true)
                                }
                            },
                            enabled = !isProbing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (dnsState.isProtectionActive) ErrorRed else PrimaryBlue
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isProbing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Menguji Handshake DoT...", fontWeight = FontWeight.SemiBold)
                            } else {
                                Icon(
                                    imageVector = if (dnsState.isProtectionActive) Icons.Default.PowerSettingsNew else Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (dnsState.isProtectionActive) "Matikan Proteksi" else "Aktifkan Proteksi Sekarang",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Quick Settings Tile Shortcut (Android 13+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    try {
                                        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                                        val component = ComponentName(context, AetherDnsTileService::class.java)
                                        statusBarManager?.requestAddTileService(
                                            component,
                                            "DNS Anti-Iklan",
                                            android.graphics.drawable.Icon.createWithResource(context, com.security.aetherdns.R.drawable.ic_tile_shield),
                                            context.mainExecutor
                                        ) { _ -> }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Tile dapat ditambahkan manual dari panel atas HP", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pasang Tombol di Quick Settings (Status Bar)", fontSize = 12.sp)
                            }
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
                                onClick = { viewModel.runAudit() },
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
                            text = "Menguji resolusi domain kontrol konektivitas, AdMob, DoubleClick, dan AppLovin.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        auditSummary?.let { summary ->
                            Spacer(modifier = Modifier.height(12.dp))
                            val isSuccessBanner = summary.isNetworkReachable && summary.isOverallProtected

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSuccessBanner) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSuccessBanner) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isSuccessBanner) SuccessGreen else ErrorRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = when {
                                                !summary.isNetworkReachable -> "Koneksi Offline / Resolver Gagal Merespons"
                                                summary.isOverallProtected -> "Hasil: ${summary.blockedCount}/${summary.totalCount} Domain Terfilter (Aman)"
                                                else -> "Hasil: ${summary.blockedCount}/${summary.totalCount} Domain Terfilter (Bocor)"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isSuccessBanner) SuccessGreen else ErrorRed
                                        )
                                        Text(
                                            text = summary.statusMessage,
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

            // 3. Setup Assistant / Guide Card with Shizuku (If permission is NOT granted)
            if (!dnsState.hasPermission) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningOrangeLight)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Section A: Aktivasi Langsung di HP via Shizuku (Wireless Debugging)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Aktivasi Langsung di HP (Tanpa PC)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = PrimaryBlue
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "AetherDNS mendukung aktivasi otomatis melalui Shizuku menggunakan Wireless Debugging (Android 11+). Cukup 1 kali aktivasi.",
                                fontSize = 12.sp,
                                color = Color(0xFF424242)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Shizuku State Card
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Status Shizuku:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when (shizukuStatus) {
                                                GranterStatus.READY -> SuccessGreen.copy(alpha = 0.15f)
                                                GranterStatus.NEEDS_AUTHORIZATION -> WarningOrange.copy(alpha = 0.15f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = when (shizukuStatus) {
                                                    GranterStatus.READY -> "Siap Terhubung"
                                                    GranterStatus.NEEDS_AUTHORIZATION -> "Perlu Otorisasi"
                                                    GranterStatus.NOT_RUNNING -> "Belum Berjalan"
                                                    GranterStatus.NOT_INSTALLED -> "Belum Terpasang"
                                                    GranterStatus.UNSUPPORTED -> "Versi Tidak Didukung"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (shizukuStatus) {
                                                    GranterStatus.READY -> SuccessGreen
                                                    GranterStatus.NEEDS_AUTHORIZATION -> WarningOrange
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = when (shizukuStatus) {
                                            GranterStatus.READY -> "Layanan Shizuku aktif. Tekan tombol di bawah untuk memberikan izin secara instan."
                                            GranterStatus.NEEDS_AUTHORIZATION -> "Shizuku sedang aktif. Izinkan AetherDNS untuk meminta izin WRITE_SECURE_SETTINGS."
                                            GranterStatus.NOT_RUNNING -> "Aplikasi Shizuku terpasang di HP Anda. Buka aplikasi Shizuku lalu tekan 'Start' via Wireless Debugging."
                                            GranterStatus.NOT_INSTALLED -> "Pasang aplikasi Shizuku (Google Play / GitHub) untuk mengaktifkan izin tanpa bantuan komputer."
                                            GranterStatus.UNSUPPORTED -> "Perbarui aplikasi Shizuku ke versi 11+."
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (shizukuStatus == GranterStatus.READY || shizukuStatus == GranterStatus.NEEDS_AUTHORIZATION) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = { viewModel.grantViaShizuku() },
                                            enabled = !isGranting,
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (isGranting) {
                                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Memproses Izin...", fontSize = 12.sp)
                                            } else {
                                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (shizukuStatus == GranterStatus.READY) "Berikan Izin Otomatis Sekarang" else "Izinkan AetherDNS di Shizuku",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "💡 Pengguna Xiaomi / MIUI / HyperOS: Pastikan opsi 'USB debugging (Security settings)' di Opsi Pengembang aktif sebelum grant.",
                                fontSize = 10.sp,
                                color = Color(0xFF616161)
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = WarningOrange.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Section B: Perintah ADB Manual via PC
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = WarningOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Atau Jalankan Perintah ADB Manual (PC)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = WarningOrange
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            val adbCommand = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

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

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.refresh() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cek Ulang", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        val targetHostname = if (selectedProfileId == "custom") customHostname.trim()
                                        else DefaultProfiles.list.find { it.id == selectedProfileId }?.hostname ?: DnsManager.DEFAULT_FALLBACK_HOSTNAME

                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("DNS Hostname", targetHostname))
                                        Toast.makeText(context, "Hostname '$targetHostname' disalin ke clipboard!", Toast.LENGTH_SHORT).show()

                                        DnsManager.openSystemDnsSettings(context)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WarningOrange)
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
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
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { viewModel.runBenchmark() },
                        enabled = !benchmark.isRunning
                    ) {
                        if (benchmark.isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (benchmark.isRunning) "Mengukur..." else "Uji Kecepatan", fontSize = 12.sp)
                    }
                }
            }

            items(DefaultProfiles.list) { profile ->
                val isSelected = selectedProfileId == profile.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedProfileId = profile.id
                            if (dnsState.hasPermission && dnsState.isProtectionActive) {
                                viewModel.applyDns(profile.hostname, probe = true)
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
                                if (dnsState.hasPermission && dnsState.isProtectionActive) {
                                    viewModel.applyDns(profile.hostname, probe = true)
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
                            benchmark.results[profile.id]?.let { result ->
                                BenchmarkChip(result = result, isFastest = benchmark.fastestId == profile.id)
                            }
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
                                Text("Gunakan ID profil NextDNS pribadi (xxxxxx.dns.nextdns.io) atau domain VPS sendiri.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (isCustomSelected) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = customHostname,
                                onValueChange = { customHostname = it },
                                label = { Text("Hostname DoT FQDN", fontSize = 12.sp) },
                                placeholder = { Text("xxxxxx.dns.nextdns.io atau dns.domain.com", fontSize = 12.sp) },
                                singleLine = true,
                                isError = isCustomInvalid,
                                supportingText = {
                                    if (isCustomInvalid) {
                                        Text(customErrorReason, color = ErrorRed, fontSize = 11.sp)
                                    } else {
                                        Text("Hanya FQDN murni (tanpa https:// atau IP address)", fontSize = 11.sp)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (dnsState.hasPermission && dnsState.isProtectionActive && customHostname.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (isCustomInvalid) {
                                            Toast.makeText(context, "Format Hostname tidak valid: $customErrorReason", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        viewModel.applyDns(customHostname.trim(), probe = true)
                                    },
                                    enabled = !isCustomInvalid && !isProbing,
                                    modifier = Modifier.align(Alignment.End),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isProbing) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Menguji...", fontSize = 12.sp)
                                    } else {
                                        Text("Uji & Terapkan", fontSize = 12.sp)
                                    }
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

@Composable
private fun BenchmarkChip(result: DotProbeResult, isFastest: Boolean) {
    val (text, color) = when (result) {
        is DotProbeResult.Success -> {
            val label = "${result.totalMs} ms (TLS ${result.handshakeMs} + query ${result.queryMs})"
            (if (isFastest) "Tercepat · $label" else label) to (if (isFastest) SuccessGreen else PrimaryBlue)
        }
        is DotProbeResult.Failure -> "Gagal: ${result.reason}" to ErrorRed
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
