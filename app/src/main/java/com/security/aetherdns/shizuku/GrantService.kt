package com.security.aetherdns.shizuku

import android.content.Context
import androidx.annotation.Keep
import kotlin.system.exitProcess

@Keep
class GrantService(private val context: Context) : IGrantService.Stub() {

    override fun grantSecureSettings(): Int {
        return try {
            val process = ProcessBuilder(
                "pm",
                "grant",
                context.packageName,
                "android.permission.WRITE_SECURE_SETTINGS"
            )
                .redirectErrorStream(true)
                .start()
            process.waitFor()
        } catch (_: Exception) {
            -1
        }
    }

    override fun destroy() {
        exitProcess(0)
    }
}
