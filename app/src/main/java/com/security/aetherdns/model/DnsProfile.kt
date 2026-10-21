package com.security.aetherdns.model

import androidx.annotation.StringRes
import com.security.aetherdns.R

/**
 * Profil resolver DoT. Preset bawaan memakai resource string (dilokalkan);
 * profil kustom tersimpan memakai [name] yang diketik pengguna.
 */
data class DnsProfile(
    val id: String,
    val hostname: String,
    val name: String = "",
    @StringRes val nameRes: Int = 0,
    @StringRes val categoryRes: Int = 0,
    @StringRes val descriptionRes: Int = 0,
    val isCustom: Boolean = false
)

object DefaultProfiles {
    val list = listOf(
        DnsProfile(
            id = "adguard_default",
            hostname = "dns.adguard-dns.com",
            nameRes = R.string.preset_adguard_name,
            categoryRes = R.string.preset_adguard_category,
            descriptionRes = R.string.preset_adguard_desc
        ),
        DnsProfile(
            id = "adguard_family",
            hostname = "family.adguard-dns.com",
            nameRes = R.string.preset_adguard_family_name,
            categoryRes = R.string.preset_adguard_family_category,
            descriptionRes = R.string.preset_adguard_family_desc
        ),
        DnsProfile(
            id = "mullvad_adblock",
            hostname = "adblock.dns.mullvad.net",
            nameRes = R.string.preset_mullvad_name,
            categoryRes = R.string.preset_mullvad_category,
            descriptionRes = R.string.preset_mullvad_desc
        ),
        DnsProfile(
            id = "cloudflare_family",
            hostname = "family.cloudflare-dns.com",
            nameRes = R.string.preset_cloudflare_family_name,
            categoryRes = R.string.preset_cloudflare_family_category,
            descriptionRes = R.string.preset_cloudflare_family_desc
        ),
        DnsProfile(
            id = "cloudflare_security",
            hostname = "security.cloudflare-dns.com",
            nameRes = R.string.preset_cloudflare_name,
            categoryRes = R.string.preset_cloudflare_category,
            descriptionRes = R.string.preset_cloudflare_desc
        ),
        DnsProfile(
            // Hostname DoT resmi Cloudflare untuk Private DNS Android
            id = "cloudflare_plain",
            hostname = "1dot1dot1dot1.cloudflare-dns.com",
            nameRes = R.string.preset_cloudflare_plain_name,
            categoryRes = R.string.preset_cloudflare_plain_category,
            descriptionRes = R.string.preset_cloudflare_plain_desc
        ),
        DnsProfile(
            id = "nextdns_public",
            hostname = "dns.nextdns.io",
            nameRes = R.string.preset_nextdns_name,
            categoryRes = R.string.preset_nextdns_category,
            descriptionRes = R.string.preset_nextdns_desc
        )
    )
}
