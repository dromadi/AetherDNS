package com.security.aetherdns

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.security.aetherdns.shortcut.AppShortcuts
import com.security.aetherdns.ui.AetherDnsApp
import com.security.aetherdns.ui.MainViewModel
import com.security.aetherdns.ui.theme.AetherDnsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as AetherDnsApplication
        MainViewModel.Factory(
            app.dnsRepository,
            app.permissionGranter,
            app.profileRepository,
            app.automationSettings,
            app.onboardingStore,
            app.wirelessActivator,
            app.nextDnsStatsSettings,
            app.diagnostics,
            app.insightStore
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppShortcuts.publish(this)
        setContent {
            AetherDnsTheme {
                AetherDnsApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Izin bisa diberikan dari luar layar ini (notifikasi aktivasi, Shizuku, ADB PC)
        viewModel.refresh()
        viewModel.onVisible()
    }

    override fun onPause() {
        viewModel.onHidden()
        super.onPause()
    }
}
