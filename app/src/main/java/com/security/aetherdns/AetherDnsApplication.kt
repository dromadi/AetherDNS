package com.security.aetherdns

import android.app.Application
import com.security.aetherdns.data.DnsRepository
import com.security.aetherdns.shizuku.PermissionGranter
import com.security.aetherdns.shizuku.ShizukuGranter

class AetherDnsApplication : Application() {
    lateinit var dnsRepository: DnsRepository
        private set

    lateinit var permissionGranter: PermissionGranter
        private set

    override fun onCreate() {
        super.onCreate()
        dnsRepository = DnsRepository(this)
        permissionGranter = ShizukuGranter(this)
    }
}
