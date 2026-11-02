package com.security.aetherdns.data

import android.content.Context
import android.provider.Settings
import com.security.aetherdns.util.DnsManager

/** Abstraksi baca/tulis `Settings.Global` Private DNS agar logika apply dapat diuji di JVM. */
interface SecureSettings {
    fun hasPermission(): Boolean
    fun mode(): String
    fun specifier(): String

    /** Menulis nilai mentah; mengembalikan false bila sistem menolak penulisan. */
    fun writeMode(mode: String): Boolean
    fun writeSpecifier(specifier: String): Boolean

    /** Dipanggil setelah konfigurasi berubah (mis. untuk menyegarkan Quick Settings Tile). */
    fun onConfigChanged() {}
}

class AndroidSecureSettings(private val context: Context) : SecureSettings {
    private val resolver = context.contentResolver

    override fun hasPermission(): Boolean = DnsManager.hasSecurePermission(context)
    override fun mode(): String = DnsManager.getPrivateDnsMode(context)
    override fun specifier(): String = DnsManager.getPrivateDnsSpecifier(context)

    override fun writeMode(mode: String): Boolean =
        Settings.Global.putString(resolver, DnsManager.SETTING_PRIVATE_DNS_MODE, mode)

    override fun writeSpecifier(specifier: String): Boolean =
        Settings.Global.putString(resolver, DnsManager.SETTING_PRIVATE_DNS_SPECIFIER, specifier)

    override fun onConfigChanged() = DnsManager.notifyTileUpdate(context)
}
