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
            id = "mullvad_adblock",
            name = "Mullvad Privacy Adblock",
            hostname = "adblock.dns.mullvad.net",
            category = "Privasi & Anti-Iklan",
            description = "Resolver zero-logging resmi dari Mullvad dengan blocklist iklan dan tracker aktif."
        ),
        DnsProfile(
            id = "cloudflare_security",
            name = "Cloudflare 1.1.1.2 (Security)",
            hostname = "security.cloudflare-dns.com",
            category = "Anti-Malware",
            description = "Fokus memblokir domain malware, phishing, ransomware, dan C2 server secara real-time."
        ),
        DnsProfile(
            id = "nextdns_public",
            name = "NextDNS Anycast",
            hostname = "dns.nextdns.io",
            category = "Resolver Bersih (Tanpa Filter)",
            description = "Anycast resolver DoT berlatensi rendah tanpa filter bawaan. Untuk anti-iklan NextDNS, gunakan opsi Hostname Kustom dengan ID profil Anda."
        )
    )
}
