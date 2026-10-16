package com.security.aetherdns

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.security.aetherdns.ui.AetherDnsApp
import com.security.aetherdns.ui.theme.AetherDnsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AetherDnsTheme {
                AetherDnsApp()
            }
        }
    }
}
