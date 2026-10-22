package com.security.aetherdns.util

/**
 * Penyusun hostname DoT NextDNS dari Config ID (6 karakter heksadesimal).
 *
 * Format DoT NextDNS: `<config-id>.dns.nextdns.io`, atau dengan identifikasi perangkat
 * `<nama-perangkat>-<config-id>.dns.nextdns.io` (spasi pada nama perangkat ditulis sebagai `--`).
 */
object NextDnsHostname {
    private const val SUFFIX = "dns.nextdns.io"
    private val CONFIG_ID = Regex("^[a-f0-9]{6}$")
    private val DEVICE_ALLOWED = Regex("[^a-z0-9 -]")

    enum class Error { INVALID_CONFIG_ID, DEVICE_NAME_TOO_LONG }

    sealed class Result {
        data class Valid(val hostname: String) : Result()
        data class Invalid(val error: Error) : Result()
    }

    /**
     * Mengekstrak Config ID dari input bebas: ID mentah (`abc123`), URL DoH
     * (`https://dns.nextdns.io/abc123`), atau hostname DoT (`abc123.dns.nextdns.io`).
     */
    fun extractConfigId(input: String): String? {
        val raw = input.trim().lowercase()
        val candidate = when {
            raw.contains("nextdns.io/") -> raw.substringAfter("nextdns.io/").substringBefore('/').substringBefore('?')
            raw.endsWith(".$SUFFIX") -> raw.removeSuffix(".$SUFFIX").substringAfterLast('-')
            else -> raw
        }
        return candidate.takeIf { CONFIG_ID.matches(it) }
    }

    fun build(configInput: String, deviceName: String = ""): Result {
        val configId = extractConfigId(configInput)
            ?: return Result.Invalid(Error.INVALID_CONFIG_ID)

        val device = sanitizeDeviceName(deviceName)
        val label = if (device.isEmpty()) configId else "$device-$configId"
        if (label.length > 63) {
            return Result.Invalid(Error.DEVICE_NAME_TOO_LONG)
        }
        return Result.Valid("$label.$SUFFIX")
    }

    /** Huruf/angka/tanda hubung dipertahankan, spasi menjadi `--`, karakter lain dibuang. */
    fun sanitizeDeviceName(name: String): String =
        name.trim()
            .lowercase()
            .replace(DEVICE_ALLOWED, "")
            .trim(' ', '-')
            .replace(Regex(" +"), "--")
}
