package com.security.aetherdns.data

data class DnsState(
    val hasPermission: Boolean = false,
    val mode: String = "off",
    val specifier: String = "",
    val isProtectionActive: Boolean = false,
    val isSystemDoTValidated: Boolean = false,
    val validatedServerName: String? = null
)
