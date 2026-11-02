package com.security.aetherdns.util

/** Domain iklan/telemetri yang dipakai audit sinkhole sistem dan pratinjau filter resolver. */
object AdTestDomains {
    val all: List<Pair<String, String>> = listOf(
        "pagead2.googlesyndication.com" to "Google AdMob",
        "adservice.google.com" to "Google Ad Services",
        "ads.doubleclick.net" to "DoubleClick",
        "applovin.com" to "AppLovin"
    )

    val domains: List<String> get() = all.map { it.first }
}

/** Menentukan apakah respons DNS untuk domain iklan berarti domain tersebut diblokir resolver. */
object FilterVerdict {
    enum class Verdict { BLOCKED, ALLOWED, UNKNOWN }

    private val SINKHOLE_IPV4 = setOf("0.0.0.0", "127.0.0.1")

    fun of(response: DnsWireFormat.Response): Verdict = when (response.header.rcode) {
        DnsWireFormat.RCODE_NXDOMAIN, DnsWireFormat.RCODE_REFUSED -> Verdict.BLOCKED
        DnsWireFormat.RCODE_NOERROR -> when {
            // NODATA: resolver filtering sering menjawab kosong untuk domain yang diblokir
            response.ipv4Answers.isEmpty() -> Verdict.BLOCKED
            response.ipv4Answers.all { it in SINKHOLE_IPV4 } -> Verdict.BLOCKED
            else -> Verdict.ALLOWED
        }
        else -> Verdict.UNKNOWN // SERVFAIL dsb.: tidak bisa disimpulkan
    }
}
