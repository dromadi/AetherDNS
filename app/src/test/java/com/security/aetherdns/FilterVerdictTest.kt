package com.security.aetherdns

import com.security.aetherdns.util.DnsWireFormat
import com.security.aetherdns.util.FilterVerdict
import com.security.aetherdns.util.FilterVerdict.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterVerdictTest {

    private fun verdict(rcode: Int, vararg ips: String) = FilterVerdict.of(
        DnsWireFormat.Response(DnsWireFormat.Header(1, true, rcode, 1, ips.size), ips.toList())
    )

    @Test
    fun nxdomainAndRefusedAreBlocked() {
        assertEquals(Verdict.BLOCKED, verdict(DnsWireFormat.RCODE_NXDOMAIN))
        assertEquals(Verdict.BLOCKED, verdict(DnsWireFormat.RCODE_REFUSED))
    }

    @Test
    fun sinkholeAddressesAndNodataAreBlocked() {
        assertEquals(Verdict.BLOCKED, verdict(DnsWireFormat.RCODE_NOERROR, "0.0.0.0"))
        assertEquals(Verdict.BLOCKED, verdict(DnsWireFormat.RCODE_NOERROR, "127.0.0.1"))
        assertEquals(Verdict.BLOCKED, verdict(DnsWireFormat.RCODE_NOERROR))
    }

    @Test
    fun anyPublicAddressMeansAllowed() {
        assertEquals(Verdict.ALLOWED, verdict(DnsWireFormat.RCODE_NOERROR, "142.250.4.100"))
        assertEquals(Verdict.ALLOWED, verdict(DnsWireFormat.RCODE_NOERROR, "0.0.0.0", "142.250.4.100"))
    }

    @Test
    fun servfailIsInconclusive() {
        assertEquals(Verdict.UNKNOWN, verdict(DnsWireFormat.RCODE_SERVFAIL))
    }
}
