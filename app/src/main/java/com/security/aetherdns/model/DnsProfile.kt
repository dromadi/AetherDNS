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
    val isCustom: Boolean = false,
    val group: PresetGroup = PresetGroup.ADS
)

/** Pengelompokan preset menurut tujuan, agar pengguna awam langsung tahu mana yang memblokir iklan. */
enum class PresetGroup(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    ADS(R.string.preset_group_ads, R.string.preset_group_ads_desc),
    FAMILY(R.string.preset_group_family, R.string.preset_group_family_desc),
    SECURITY(R.string.preset_group_security, R.string.preset_group_security_desc),
    PLAIN(R.string.preset_group_plain, R.string.preset_group_plain_desc)
}

object DefaultProfiles {
    /** Urutan = urutan tampil; [grouped] menyusunnya per [PresetGroup]. */
    val list = listOf(
        DnsProfile(
            id = "adguard_default",
            hostname = "dns.adguard-dns.com",
            nameRes = R.string.preset_adguard_name,
            categoryRes = R.string.preset_adguard_category,
            descriptionRes = R.string.preset_adguard_desc
        ),
        DnsProfile(
            // Control D Free: profil dipilih lewat SNI (p2 = malware + iklan & pelacak)
            id = "controld_free_ads",
            hostname = "p2.freedns.controld.com",
            nameRes = R.string.preset_controld_name,
            categoryRes = R.string.preset_controld_category,
            descriptionRes = R.string.preset_controld_desc
        ),
        DnsProfile(
            id = "adguard_family",
            group = PresetGroup.FAMILY,
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
            id = "mullvad_extended",
            hostname = "extended.dns.mullvad.net",
            nameRes = R.string.preset_mullvad_extended_name,
            categoryRes = R.string.preset_mullvad_extended_category,
            descriptionRes = R.string.preset_mullvad_extended_desc
        ),
        DnsProfile(
            id = "mullvad_all",
            group = PresetGroup.FAMILY,
            hostname = "all.dns.mullvad.net",
            nameRes = R.string.preset_mullvad_all_name,
            categoryRes = R.string.preset_mullvad_all_category,
            descriptionRes = R.string.preset_mullvad_all_desc
        ),
        DnsProfile(
            id = "cloudflare_family",
            group = PresetGroup.FAMILY,
            hostname = "family.cloudflare-dns.com",
            nameRes = R.string.preset_cloudflare_family_name,
            categoryRes = R.string.preset_cloudflare_family_category,
            descriptionRes = R.string.preset_cloudflare_family_desc
        ),
        DnsProfile(
            id = "quad9",
            group = PresetGroup.SECURITY,
            hostname = "dns.quad9.net",
            nameRes = R.string.preset_quad9_name,
            categoryRes = R.string.preset_quad9_category,
            descriptionRes = R.string.preset_quad9_desc
        ),
        DnsProfile(
            id = "cloudflare_security",
            group = PresetGroup.SECURITY,
            hostname = "security.cloudflare-dns.com",
            nameRes = R.string.preset_cloudflare_name,
            categoryRes = R.string.preset_cloudflare_category,
            descriptionRes = R.string.preset_cloudflare_desc
        ),
        DnsProfile(
            // Hostname DoT resmi Cloudflare untuk Private DNS Android
            id = "cloudflare_plain",
            group = PresetGroup.PLAIN,
            hostname = "1dot1dot1dot1.cloudflare-dns.com",
            nameRes = R.string.preset_cloudflare_plain_name,
            categoryRes = R.string.preset_cloudflare_plain_category,
            descriptionRes = R.string.preset_cloudflare_plain_desc
        ),
        DnsProfile(
            id = "nextdns_public",
            group = PresetGroup.PLAIN,
            hostname = "dns.nextdns.io",
            nameRes = R.string.preset_nextdns_name,
            categoryRes = R.string.preset_nextdns_category,
            descriptionRes = R.string.preset_nextdns_desc
        )
    )

    /** Preset per grup dengan urutan grup tetap; grup kosong dilewati. */
    val grouped: List<Pair<PresetGroup, List<DnsProfile>>>
        get() = PresetGroup.entries.mapNotNull { group ->
            list.filter { it.group == group }.takeIf { it.isNotEmpty() }?.let { group to it }
        }
}
