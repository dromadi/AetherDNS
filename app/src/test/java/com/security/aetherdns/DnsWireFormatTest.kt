package com.security.aetherdns

import com.security.aetherdns.util.DnsWireFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsWireFormatTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun buildsRfc1035QueryForExampleCom() {
        val query = DnsWireFormat.buildQuery(id = 0xBEEF, name = "example.com")
        val expected = bytes(
            0xBE, 0xEF, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // header, RD=1, QDCOUNT=1
            7, 'e'.code, 'x'.code, 'a'.code, 'm'.code, 'p'.code, 'l'.code, 'e'.code,
            3, 'c'.code, 'o'.code, 'm'.code, 0,
            0x00, 0x01, 0x00, 0x01 // QTYPE=A, QCLASS=IN
        )
        assertArrayEquals(expected, query)
    }

    @Test
    fun trailingDotIsIgnored() {
        assertArrayEquals(
            DnsWireFormat.buildQuery(1, "example.com"),
            DnsWireFormat.buildQuery(1, "example.com.")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyLabel() {
        DnsWireFormat.buildQuery(1, "example..com")
    }

    @Test
    fun framePrefixesBigEndianLength() {
        val framed = DnsWireFormat.frame(ByteArray(300))
        assertEquals(302, framed.size)
        assertEquals(0x01, framed[0].toInt() and 0xFF)
        assertEquals(0x2C, framed[1].toInt() and 0xFF)
    }

    @Test
    fun parsesResponseHeader() {
        // ID=0xBEEF, QR=1 RD=1 RA=1 RCODE=3 (NXDOMAIN), QDCOUNT=1, ANCOUNT=2
        val header = DnsWireFormat.parseHeader(bytes(0xBE, 0xEF, 0x81, 0x83, 0, 1, 0, 2, 0, 0, 0, 0))!!
        assertEquals(0xBEEF, header.id)
        assertTrue(header.isResponse)
        assertEquals(DnsWireFormat.RCODE_NXDOMAIN, header.rcode)
        assertEquals(1, header.questionCount)
        assertEquals(2, header.answerCount)
    }

    @Test
    fun queryEchoIsNotTreatedAsResponse() {
        val header = DnsWireFormat.parseHeader(DnsWireFormat.buildQuery(7, "example.com"))!!
        assertFalse(header.isResponse)
    }

    @Test
    fun truncatedMessageReturnsNull() {
        assertNull(DnsWireFormat.parseHeader(ByteArray(11)))
    }
}
