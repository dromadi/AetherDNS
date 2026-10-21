package com.security.aetherdns.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.security.aetherdns.R
import com.security.aetherdns.adb.ActivationState
import com.security.aetherdns.adb.PairingCode
import com.security.aetherdns.ui.theme.ErrorRed
import com.security.aetherdns.ui.theme.PrimaryBlue
import com.security.aetherdns.ui.theme.SuccessGreen
import com.security.aetherdns.ui.theme.WarningOrange
import com.security.aetherdns.util.SystemShortcuts

/** State + aksi aktivasi bawaan untuk [PermissionAssistantCard]; `null` bila perangkat < Android 11. */
data class NativeActivationUi(
    val state: ActivationState,
    val onShowCodeBox: () -> Unit,
    val onSubmitCode: (String) -> Unit
)

/** Aktivasi bawaan: kode penyambungan Wireless debugging dimasukkan lewat notifikasi atau langsung di sini. */
@Composable
internal fun NativeActivationSection(ui: NativeActivationUi) {
    val context = LocalContext.current
    val state = ui.state
    val working = state.isWorking

    fun startNotificationFlow() {
        ui.onShowCodeBox()
        if (SystemShortcuts.openWirelessDebugging(context) == SystemShortcuts.DeveloperTarget.ABOUT_PHONE) {
            Toast.makeText(context, R.string.shizuku_enable_developer_options, Toast.LENGTH_LONG).show()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startNotificationFlow()
        else Toast.makeText(context, R.string.native_notifications_denied, Toast.LENGTH_LONG).show()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.PhonelinkLock, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.native_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryBlue)
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(stringResource(R.string.native_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(10.dp))

    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Langkah bernomor; teks tetap satu string terlokalisasi ("1. …\n2. …") agar mudah diterjemahkan
            stringResource(R.string.native_steps).lines().filter { it.isNotBlank() }.forEachIndexed { index, line ->
                NumberedStep(number = index + 1, text = line.substringAfter(". ", line))
            }
            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startNotificationFlow()
                    }
                },
                enabled = !working,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.native_show_code_box), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }

            ActivationStatus(state)

            Spacer(modifier = Modifier.height(10.dp))
            ManualCodeEntry(enabled = !working, onSubmit = ui.onSubmitCode)

            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.native_forget_hint), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActivationStatus(state: ActivationState) {
    val (text, color) = when (state) {
        ActivationState.Idle -> return
        ActivationState.AwaitingCode -> stringResource(R.string.native_waiting) to MaterialTheme.colorScheme.onSurfaceVariant
        is ActivationState.Working -> state.step.toUiText().resolve() to PrimaryBlue
        ActivationState.Success -> stringResource(R.string.native_success) to SuccessGreen
        is ActivationState.Failed -> state.error.toUiText().resolve() to ErrorRed
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.isWorking || state == ActivationState.AwaitingCode) {
            CircularProgressIndicator(color = color, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun ManualCodeEntry(enabled: Boolean, onSubmit: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    // Membaca konfigurasi agar status layar terpisah dihitung ulang saat ukuran jendela berubah
    LocalConfiguration.current
    val inMultiWindow = (LocalContext.current as? Activity)?.isInMultiWindowMode == true
    Text(stringResource(R.string.native_manual_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!inMultiWindow) {
        // Layar penuh: dialog kode di Settings sudah tertutup saat pengguna kembali ke sini
        Text(
            stringResource(R.string.native_manual_split_only),
            fontSize = 12.sp,
            color = WarningOrange,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { input -> code = input.filter { it.isDigit() }.take(PairingCode.LENGTH) },
            placeholder = { Text(stringResource(R.string.native_code_hint), fontSize = 12.sp) },
            singleLine = true,
            enabled = enabled && inMultiWindow,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = { onSubmit(code) },
            enabled = enabled && inMultiWindow && PairingCode.normalize(code) != null,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(stringResource(R.string.native_connect), fontSize = 12.sp)
        }
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(PrimaryBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 1.dp))
    }
}
