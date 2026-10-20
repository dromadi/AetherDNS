package com.security.aetherdns.adb

import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Koneksi ADB ke adbd perangkat sendiri dengan identitas sekali pakai.
 *
 * Kunci RSA + sertifikat self-signed dibuat di memori per proses dan tidak pernah ditulis ke disk:
 * aktivasi hanya perlu sekali, jadi setelah proses berakhir kunci ikut hilang. Satu instance per proses
 * karena libadb menyimpan `SSLContext` secara statis untuk kunci pertama yang dipakai.
 */
internal class AdbIdentity private constructor() : AbsAdbConnectionManager() {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_SIZE) }.generateKeyPair()

    private val selfSigned: Certificate by lazy {
        val name = X500Name("CN=$DEVICE_NAME")
        val now = System.currentTimeMillis()
        // Ditandatangani lewat provider JCA platform; BouncyCastle hanya dipakai untuk menyusun struktur X.509
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(now),
            Date(now - TimeUnit.DAYS.toMillis(1)),
            Date(now + TimeUnit.DAYS.toMillis(VALIDITY_DAYS)),
            name,
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    init {
        api = Build.VERSION.SDK_INT
        setTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = keyPair.private

    override fun getCertificate(): Certificate = selfSigned

    /** Nama yang tampil di daftar "Perangkat yang disambungkan" pada Wireless debugging. */
    override fun getDeviceName(): String = DEVICE_NAME

    companion object {
        const val DEVICE_NAME = "AetherDNS"
        private const val KEY_SIZE = 2048
        private const val VALIDITY_DAYS = 365L
        private const val CONNECT_TIMEOUT_S = 10L

        val instance: AdbIdentity by lazy { AdbIdentity() }
    }
}
