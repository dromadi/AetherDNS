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

    /** Respons untuk query example.com (ID 0x1234) dengan [answers] record A memakai pointer kompresi ke offset 12. */
    private fun response(rcode: Int, vararg answers: List<Int>): ByteArray {
        val question = DnsWireFormat.buildQuery(0x1234, "example.com").drop(12)
        val header = bytes(0x12, 0x34, 0x81, 0x80 or rcode, 0, 1, 0, answers.size, 0, 0, 0, 0).toList()
        val records = answers.flatMap { ip ->
            listOf(0xC0, 0x0C, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4) + ip
        }.map { it.toByte() }
        return (header + question + records).toByteArray()
    }

    @Test
    fun parsesCompressedARecords() {
        val parsed = DnsWireFormat.parseResponse(response(0, listOf(93, 184, 215, 14), listOf(0, 0, 0, 0)))!!
        assertEquals(0x1234, parsed.header.id)
        assertEquals(listOf("93.184.215.14", "0.0.0.0"), parsed.ipv4Answers)
    }

    @Test
    fun skipsNonARecords() {
        // Record CNAME (type 5) dengan RDATA berupa pointer, lalu satu record A
        val cname = listOf(0xC0, 0x0C, 0, 5, 0, 1, 0, 0, 0, 60, 0, 2, 0xC0, 0x0C)
        val a = listOf(0xC0, 0x0C, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 1, 2, 3, 4)
        val base = response(0).toMutableList()
        base[7] = 2 // ANCOUNT
        val message = (base + (cname + a).map { it.toByte() }).toByteArray()
        assertEquals(listOf("1.2.3.4"), DnsWireFormat.parseResponse(message)!!.ipv4Answers)
    }

    @Test
    fun truncatedAnswerReturnsNull() {
        val full = response(0, listOf(1, 2, 3, 4))
        assertNull(DnsWireFormat.parseResponse(full.copyOf(full.size - 2)))
    }

    @Test
    fun answerCountBeyondMessageReturnsNull() {
        val message = response(0)
        message[7] = 3 // klaim 3 answer, tidak ada data
        assertNull(DnsWireFormat.parseResponse(message))
    }
}
