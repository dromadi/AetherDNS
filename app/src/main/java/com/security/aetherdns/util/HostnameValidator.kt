package com.security.aetherdns.util

object HostnameValidator {
    sealed class Result {
        data class Valid(val fqdn: String) : Result()
        data class Invalid(val reason: String) : Result()
    }

    private val LABEL = Regex("^(?!-)[a-z0-9-]{1,63}(?<!-)$")
    private val TLD = Regex("^(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun validate(input: String): Result {
        val raw = input.trim()
        if (raw.isEmpty()) return Result.Invalid("Hostname tidak boleh kosong")
        if (raw.contains("://")) return Result.Invalid("Hapus skema URL (https://, tls://) — DoT hanya menerima FQDN")
        if (raw.any { it == '/' || it == '?' || it == '#' }) return Result.Invalid("Hapus path/query — hanya hostname")
        if (raw.contains(':')) return Result.Invalid("Port/IPv6 tidak didukung — Android selalu memakai port 853")
        if (IPV4.matches(raw)) return Result.Invalid("Alamat IP tidak didukung — sertifikat TLS diverifikasi berdasarkan FQDN")

        val ascii = try {
            java.net.IDN.toASCII(raw.removeSuffix("."), java.net.IDN.USE_STD3_ASCII_RULES)
        } catch (_: IllegalArgumentException) {
            return Result.Invalid("Karakter hostname tidak valid")
        }.lowercase()

        if (ascii.length > 253) return Result.Invalid("Hostname melebihi 253 karakter")

        val labels = ascii.split('.')
        if (labels.size < 2) return Result.Invalid("FQDN harus memiliki minimal satu titik (mis. dns.contoh.com)")
        if (!labels.all { LABEL.matches(it) }) return Result.Invalid("Label hanya boleh a-z, 0-9, '-' (maks. 63 karakter, tidak diawali/diakhiri '-')")
        if (!TLD.matches(labels.last())) return Result.Invalid("TLD tidak valid")

        return Result.Valid(ascii)
    }
}
