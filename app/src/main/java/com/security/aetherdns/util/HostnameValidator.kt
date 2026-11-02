package com.security.aetherdns.util

/** Alasan hostname ditolak; teks yang ditampilkan ke pengguna dipetakan di lapisan UI (strings.xml). */
enum class HostnameError {
    EMPTY,
    URL_SCHEME,
    PATH_OR_QUERY,
    PORT_OR_IPV6,
    IP_ADDRESS,
    INVALID_CHARACTERS,
    TOO_LONG,
    MISSING_DOT,
    INVALID_LABEL,
    INVALID_TLD
}

object HostnameValidator {
    sealed class Result {
        data class Valid(val fqdn: String) : Result()
        data class Invalid(val error: HostnameError) : Result()
    }

    private val LABEL = Regex("^(?!-)[a-z0-9-]{1,63}(?<!-)$")
    private val TLD = Regex("^(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun validate(input: String): Result {
        val raw = input.trim()
        if (raw.isEmpty()) return Result.Invalid(HostnameError.EMPTY)
        if (raw.contains("://")) return Result.Invalid(HostnameError.URL_SCHEME)
        if (raw.any { it == '/' || it == '?' || it == '#' }) return Result.Invalid(HostnameError.PATH_OR_QUERY)
        if (raw.contains(':')) return Result.Invalid(HostnameError.PORT_OR_IPV6)
        if (IPV4.matches(raw)) return Result.Invalid(HostnameError.IP_ADDRESS)

        val ascii = try {
            java.net.IDN.toASCII(raw.removeSuffix("."), java.net.IDN.USE_STD3_ASCII_RULES)
        } catch (_: IllegalArgumentException) {
            return Result.Invalid(HostnameError.INVALID_CHARACTERS)
        }.lowercase()

        if (ascii.length > 253) return Result.Invalid(HostnameError.TOO_LONG)

        val labels = ascii.split('.')
        if (labels.size < 2) return Result.Invalid(HostnameError.MISSING_DOT)
        if (!labels.all { LABEL.matches(it) }) return Result.Invalid(HostnameError.INVALID_LABEL)
        if (!TLD.matches(labels.last())) return Result.Invalid(HostnameError.INVALID_TLD)

        return Result.Valid(ascii)
    }
}
