package com.security.aetherdns

import android.app.Application
import com.security.aetherdns.data.DnsRepository

class AetherDnsApplication : Application() {
    lateinit var dnsRepository: DnsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        dnsRepository = DnsRepository(this)
    }
}
