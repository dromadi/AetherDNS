package com.security.aetherdns.model

data class DnsProfile(
    val id: String,
    val name: String,
    val hostname: String,
    val category: String,
    val description: String,
    val isCustom: Boolean = false
)

object DefaultProfiles {
    val list = listOf(
        DnsProfile(
            id = "adguard_default",
            name = "AdGuard DNS (Rekomendasi)",
            hostname = "dns.adguard-dns.com",
            category = "Anti-Iklan & Tracker",
            description = "Memblokir iklan banner, pop-up, rewarded ads di aplikasi Android dan pelacak telemetri."
        ),
        DnsProfile(
            id = "adguard_family",
            name = "AdGuard Family",
            hostname = "family.adguard-dns.com",
            category = "Keluarga & Anti-Iklan",
            description = "Proteksi iklan aplikasi sekaligus memblokir situs konten dewasa dan mengaktifkan SafeSearch."
        ),
        DnsProfile(
            id = "nextdns_public",
            name = "NextDNS Public",
            hostname = "dns.nextdns.io",
            category = "Kecepatan & Privasi",
            description = "Anycast resolver DoT berkecepatan tinggi dengan proteksi dasar terhadap ancaman cyber."
        ),
        DnsProfile(
            id = "cloudflare_security",
            name = "Cloudflare 1.1.1.2 (Security)",
            hostname = "security.cloudflare-dns.com",
            category = "Anti-Malware",
            description = "Fokus memblokir domain malware, phishing, ransomware, dan C2 server secara real-time."
        ),
        DnsProfile(
            id = "mullvad_adblock",
            name = "Mullvad Privacy Adblock",
            hostname = "adblock.doh.mullvad.net",
            category = "Privasi Maksimal",
            description = "Resolver zero-logging dengan blocklist iklan dan tracker yang agresif."
        )
    )
}
