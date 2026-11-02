package com.security.aetherdns.shizuku

import android.content.Context
import androidx.annotation.Keep
import kotlin.system.exitProcess

/**
 * UserService Shizuku yang berjalan di proses terpisah dengan UID shell (2000) atau root (Sui).
 * Paket dan izin target ditetapkan di sini, bukan dikirim pemanggil, agar proses istimewa ini
 * tidak dapat dipakai untuk memberikan izin lain.
 */
@Keep
class GrantService(private val context: Context) : IGrantService.Stub() {

    override fun grantSecureSettings(): String {
        return try {
            val process = ProcessBuilder(
                "pm",
                "grant",
                context.packageName,
                "android.permission.WRITE_SECURE_SETTINGS"
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            "${process.waitFor()}\n$output"
        } catch (e: Exception) {
            "-1\n${e.javaClass.simpleName}: ${e.message}"
        }
    }

    override fun destroy() {
        exitProcess(0)
    }
}
