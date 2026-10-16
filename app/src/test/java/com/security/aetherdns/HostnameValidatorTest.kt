package com.security.aetherdns

import com.security.aetherdns.util.HostnameValidator
import org.junit.Assert.*
import org.junit.Test

class HostnameValidatorTest {

    @Test
    fun testValidHostnames() {
        val validCases = listOf(
            "dns.adguard-dns.com" to "dns.adguard-dns.com",
            "security.cloudflare-dns.com" to "security.cloudflare-dns.com",
            "abc123.dns.nextdns.io" to "abc123.dns.nextdns.io",
            "adblock.dns.mullvad.net" to "adblock.dns.mullvad.net",
            "  dns.example.com.  " to "dns.example.com", // trimmed, trailing dot removed
            "sub.domain.co.id" to "sub.domain.co.id",
            "DNS.GOOGLE.COM" to "dns.google.com" // normalized to lowercase
        )

        for ((input, expected) in validCases) {
            val result = HostnameValidator.validate(input)
            assertTrue("Expected valid for '$input' but was $result", result is HostnameValidator.Result.Valid)
            assertEquals(expected, (result as HostnameValidator.Result.Valid).fqdn)
        }
    }

    @Test
    fun testRejectsEmpty() {
        val result = HostnameValidator.validate("   ")
        assertTrue(result is HostnameValidator.Result.Invalid)
    }

    @Test
    fun testRejectsUrlScheme() {
        val schemes = listOf("https://dns.adguard.com", "http://dns.adguard.com", "tls://dns.adguard.com")
        for (item in schemes) {
            val result = HostnameValidator.validate(item)
            assertTrue("Expected invalid for scheme '$item'", result is HostnameValidator.Result.Invalid)
            assertTrue((result as HostnameValidator.Result.Invalid).reason.contains("skema", ignoreCase = true))
        }
    }

    @Test
    fun testRejectsPathOrQuery() {
        val paths = listOf("dns.adguard.com/dns-query", "dns.adguard.com?arg=1", "dns.adguard.com#hash")
        for (item in paths) {
            val result = HostnameValidator.validate(item)
            assertTrue("Expected invalid for path/query '$item'", result is HostnameValidator.Result.Invalid)
        }
    }

    @Test
    fun testRejectsPortAndIpv6() {
        val ports = listOf("dns.adguard.com:853", "[2001:4860:4860::8888]", "::1")
        for (item in ports) {
            val result = HostnameValidator.validate(item)
            assertTrue("Expected invalid for port/ipv6 '$item'", result is HostnameValidator.Result.Invalid)
        }
    }

    @Test
    fun testRejectsRawIpv4() {
        val ips = listOf("1.1.1.1", "8.8.8.8", "94.140.14.14", "192.168.1.1")
        for (item in ips) {
            val result = HostnameValidator.validate(item)
            assertTrue("Expected invalid for IP '$item'", result is HostnameValidator.Result.Invalid)
            assertTrue((result as HostnameValidator.Result.Invalid).reason.contains("IP", ignoreCase = true))
        }
    }

    @Test
    fun testRejectsInvalidLabels() {
        val badLabels = listOf(
            "-bad.com",
            "bad-.com",
            "singlelabel",
            "a".repeat(64) + ".com",
            "bad..label.com"
        )
        for (item in badLabels) {
            val result = HostnameValidator.validate(item)
            assertTrue("Expected invalid for '$item'", result is HostnameValidator.Result.Invalid)
        }
    }
}
