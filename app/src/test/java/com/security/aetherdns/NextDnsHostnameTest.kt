package com.security.aetherdns

import com.security.aetherdns.util.HostnameValidator
import com.security.aetherdns.util.NextDnsHostname
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextDnsHostnameTest {

    private fun hostname(config: String, device: String = "") =
        (NextDnsHostname.build(config, device) as NextDnsHostname.Result.Valid).hostname

    @Test
    fun buildsPlainHostnameFromConfigId() {
        assertEquals("abc123.dns.nextdns.io", hostname("abc123"))
        assertEquals("abc123.dns.nextdns.io", hostname("  ABC123 "))
    }

    @Test
    fun extractsConfigIdFromDohUrlAndDotHostname() {
        assertEquals("abc123", NextDnsHostname.extractConfigId("https://dns.nextdns.io/abc123"))
        assertEquals("abc123", NextDnsHostname.extractConfigId("https://dns.nextdns.io/abc123/My%20Phone"))
        assertEquals("abc123", NextDnsHostname.extractConfigId("abc123.dns.nextdns.io"))
        assertEquals("abc123", NextDnsHostname.extractConfigId("pixel--8-abc123.dns.nextdns.io"))
    }

    @Test
    fun rejectsInvalidConfigIds() {
        assertNull(NextDnsHostname.extractConfigId("abc12"))
        assertNull(NextDnsHostname.extractConfigId("abcxyz"))
        assertTrue(NextDnsHostname.build("") is NextDnsHostname.Result.Invalid)
    }

    @Test
    fun deviceNameSpacesBecomeDoubleDash() {
        assertEquals("pixel--8-abc123.dns.nextdns.io", hostname("abc123", "Pixel 8"))
        assertEquals("johns--phone-abc123.dns.nextdns.io", hostname("abc123", "  John's   Phone! "))
    }

    @Test
    fun builtHostnamesPassFqdnValidation() {
        for (device in listOf("", "Pixel 8", "-leading", "a b c")) {
            val result = HostnameValidator.validate(hostname("abc123", device))
            assertTrue("device='$device' -> $result", result is HostnameValidator.Result.Valid)
        }
    }

    @Test
    fun overlongDeviceNameIsRejected() {
        assertTrue(NextDnsHostname.build("abc123", "x".repeat(60)) is NextDnsHostname.Result.Invalid)
    }
}
