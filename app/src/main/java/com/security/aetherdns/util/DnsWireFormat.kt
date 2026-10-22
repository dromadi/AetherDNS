package com.security.aetherdns.util

import java.io.ByteArrayOutputStream

/**
 * Encoder/decoder minimal pesan DNS (RFC 1035) untuk kebutuhan probe DoT (RFC 7858).
 * Hanya mendukung satu pertanyaan (QDCOUNT = 1) dan pembacaan header respons.
 */
object DnsWireFormat {
    const val TYPE_A = 1
    const val CLASS_IN = 1

    const val RCODE_NOERROR = 0
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3
    const val RCODE_REFUSED = 5

    private const val HEADER_SIZE = 12

    data class Header(
        val id: Int,
        val isResponse: Boolean,
        val rcode: Int,
        val questionCount: Int,
        val answerCount: Int
    )

    /** Respons DNS: header + alamat IPv4 dari semua record A di bagian answer. */
    data class Response(val header: Header, val ipv4Answers: List<String>)

    /** Membangun query rekursif (RD = 1) untuk [name] dengan tipe [type]. */
    fun buildQuery(id: Int, name: String, type: Int = TYPE_A): ByteArray {
        require(id in 0..0xFFFF) { "ID DNS harus 16-bit" }
        val out = ByteArrayOutputStream()
        out.writeShort(id)
        out.writeShort(0x0100) // QR=0, OPCODE=0, RD=1
        out.writeShort(1)      // QDCOUNT
        out.writeShort(0)      // ANCOUNT
        out.writeShort(0)      // NSCOUNT
        out.writeShort(0)      // ARCOUNT
        for (label in name.trimEnd('.').split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            require(bytes.size in 1..63) { "Label DNS tidak valid: '$label'" }
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        out.writeShort(type)
        out.writeShort(CLASS_IN)
        return out.toByteArray()
    }

    /** Membungkus pesan DNS dengan prefiks panjang 2-byte sesuai framing TCP/DoT (RFC 7858 §3.3). */
    fun frame(message: ByteArray): ByteArray {
        require(message.size <= 0xFFFF) { "Pesan DNS terlalu besar" }
        return byteArrayOf((message.size shr 8).toByte(), message.size.toByte()) + message
    }

    /** Membaca header respons DNS; mengembalikan null bila pesan terlalu pendek. */
    fun parseHeader(message: ByteArray): Header? {
        if (message.size < HEADER_SIZE) return null
        val flags = message.u16(2)
        return Header(
            id = message.u16(0),
            isResponse = flags and 0x8000 != 0,
            rcode = flags and 0x000F,
            questionCount = message.u16(4),
            answerCount = message.u16(6)
        )
    }

    /**
     * Membaca header dan record A pada bagian answer. Mendukung kompresi nama (RFC 1035 §4.1.4).
     * Mengembalikan null bila pesan terpotong atau tidak valid.
     */
    fun parseResponse(message: ByteArray): Response? {
        val header = parseHeader(message) ?: return null
        var offset = HEADER_SIZE
        repeat(header.questionCount) {
            offset = skipName(message, offset) ?: return null
            offset += 4 // QTYPE + QCLASS
            if (offset > message.size) return null
        }
        val ipv4 = mutableListOf<String>()
        repeat(header.answerCount) {
            offset = skipName(message, offset) ?: return null
            if (offset + 10 > message.size) return null
            val type = message.u16(offset)
            val rdLength = message.u16(offset + 8)
            val rdStart = offset + 10
            if (rdStart + rdLength > message.size) return null
            if (type == TYPE_A && rdLength == 4) {
                ipv4 += (0 until 4).joinToString(".") { (message[rdStart + it].toInt() and 0xFF).toString() }
            }
            offset = rdStart + rdLength
        }
        return Response(header, ipv4)
    }

    /** Melewati satu nama domain (label atau pointer kompresi); null bila keluar batas pesan. */
    private fun skipName(message: ByteArray, start: Int): Int? {
        var offset = start
        while (offset < message.size) {
            val length = message[offset].toInt() and 0xFF
            when {
                length == 0 -> return offset + 1
                length and 0xC0 == 0xC0 -> return (offset + 2).takeIf { it <= message.size }
                else -> offset += length + 1
            }
        }
        return null
    }

    private fun ByteArray.u16(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }
}
