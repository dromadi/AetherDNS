package com.security.aetherdns

import android.app.Application
import com.security.aetherdns.data.DnsRepository
import com.security.aetherdns.shizuku.PermissionGranter
import com.security.aetherdns.shizuku.ShizukuGranter
import rikka.sui.Sui

class AetherDnsApplication : Application() {
    lateinit var dnsRepository: DnsRepository
        private set

    lateinit var permissionGranter: PermissionGranter
        private set

    override fun onCreate() {
        super.onCreate()
        // Sui (Shizuku via Magisk/KernelSU) hanya mengirim binder bila diinisialisasi; no-op tanpa Sui
        Sui.init(BuildConfig.APPLICATION_ID)
        dnsRepository = DnsRepository(this)
        permissionGranter = ShizukuGranter(this)
    }
}
