package com.security.aetherdns.shortcut

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.security.aetherdns.AetherDnsApplication
import com.security.aetherdns.MainActivity
import com.security.aetherdns.R
import com.security.aetherdns.data.ApplyResult
import com.security.aetherdns.data.PauseResult
import com.security.aetherdns.ui.UiText
import com.security.aetherdns.ui.toUiText
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Activity transparan tanpa UI: menjalankan aksi App Shortcut, menampilkan Toast, lalu langsung selesai. */
class ShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as AetherDnsApplication).dnsRepository

        if (!repository.loadCurrentState().hasPermission) {
            // Tanpa izin, arahkan ke asisten izin di aplikasi utama
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        lifecycleScope.launch {
            // Penulisan setting diselesaikan walau activity dihancurkan di tengah jalan
            val message = withContext(NonCancellable) {
                when (intent?.action) {
                    AppShortcuts.ACTION_ENABLE -> when (val result = repository.enableQuick()) {
                        is ApplyResult.Success -> UiText.of(R.string.apply_success, result.hostname)
                        is ApplyResult.Failure -> result.error.toUiText()
                    }
                    AppShortcuts.ACTION_DISABLE ->
                        UiText.of(if (repository.disable()) R.string.disable_success else R.string.disable_failed)
                    AppShortcuts.ACTION_PAUSE -> when (val result = repository.pause(AppShortcuts.PAUSE_MINUTES * 60_000L)) {
                        is PauseResult.Paused -> UiText.of(
                            R.string.pause_success,
                            AppShortcuts.PAUSE_MINUTES,
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(result.untilMs))
                        )
                        is PauseResult.Failure -> result.error.toUiText()
                    }
                    else -> null
                }
            }
            message?.let { Toast.makeText(applicationContext, it.resolve(applicationContext), Toast.LENGTH_LONG).show() }
            finish()
        }
    }
}
