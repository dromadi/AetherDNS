package com.security.aetherdns

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.security.aetherdns.ui.AetherDnsApp
import com.security.aetherdns.ui.MainViewModel
import com.security.aetherdns.ui.theme.AetherDnsTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as AetherDnsApplication
        MainViewModel.Factory(app.dnsRepository, app.permissionGranter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AetherDnsTheme {
                AetherDnsApp(viewModel = viewModel)
            }
        }
    }
}
