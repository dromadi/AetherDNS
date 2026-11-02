package com.security.aetherdns.shizuku

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.security.aetherdns.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

enum class GranterStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    NEEDS_AUTHORIZATION,
    READY,
    UNSUPPORTED
}

interface PermissionGranter {
    fun status(): GranterStatus
    suspend fun requestPermission(): Boolean
    suspend fun grant(): Result<Unit>
}

/** Penyebab kegagalan pemberian izin via Shizuku; teks untuk pengguna dipetakan di lapisan UI. */
sealed class GrantError {
    /** `pm grant` ditolak oleh kebijakan OEM (MIUI/HyperOS, ColorOS, dsb.). */
    data object OemRestricted : GrantError()
    data class PmFailed(val exitCode: String, val firstOutputLine: String?) : GrantError()
    data object Timeout : GrantError()
    data object ServiceUnavailable : GrantError()
}

class GrantException(val error: GrantError) : Exception(error.toString())

/** Menerjemahkan keluaran `GrantService` menjadi hasil bertipe. */
object GrantOutputParser {
    fun toResult(raw: String, permissionGranted: Boolean): Result<Unit> {
        // Sumber kebenaran adalah status izin aktual, bukan exit code `pm grant`
        if (permissionGranted) return Result.success(Unit)

        val exitCode = raw.substringBefore('\n').trim()
        val output = raw.substringAfter('\n', "").trim()
        val error = when {
            isOemRestriction(output) -> GrantError.OemRestricted
            else -> GrantError.PmFailed(exitCode, output.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() })
        }
        return Result.failure(GrantException(error))
    }

    fun isOemRestriction(output: String): Boolean =
        output.contains("SecurityException") || output.contains("GRANT_RUNTIME_PERMISSIONS")
}

class ShizukuGranter(private val context: Context) : PermissionGranter {

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(context, GrantService::class.java))
            .daemon(false) // Proses mati otomatis setelah unbind -> 0% overhead
            .processNameSuffix("grant")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    override fun status(): GranterStatus {
        // Binder dicek lebih dulu: Sui (Shizuku via Magisk) menyediakan binder tanpa aplikasi Shizuku terpasang
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            return if (isShizukuAppInstalled()) GranterStatus.NOT_RUNNING else GranterStatus.NOT_INSTALLED
        }

        return runCatching {
            when {
                Shizuku.isPreV11() -> GranterStatus.UNSUPPORTED
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> GranterStatus.READY
                else -> GranterStatus.NEEDS_AUTHORIZATION
            }
        }.getOrDefault(GranterStatus.NOT_RUNNING) // binder mati di antara pengecekan
    }

    override suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { cont ->
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                if (cont.isActive) cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }

        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                cont.resume(true)
                return@suspendCancellableCoroutine
            }
            Shizuku.addRequestPermissionResultListener(listener)
            cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            // IllegalStateException bila binder Shizuku mati (mis. Wireless Debugging dimatikan)
            Shizuku.removeRequestPermissionResultListener(listener)
            if (cont.isActive) cont.resume(false)
        }
    }

    override suspend fun grant(): Result<Unit> {
        val connection = GrantConnection()
        return try {
            val service = withTimeout(BIND_TIMEOUT_MS) { connection.bind() }
            // Callback ServiceConnection berjalan di main thread; panggilan binder sinkron dipindah ke IO
            val raw = withContext(Dispatchers.IO) { service.grantSecureSettings() }
            GrantOutputParser.toResult(raw, hasSecurePermission())
        } catch (e: TimeoutCancellationException) {
            Result.failure(GrantException(GrantError.Timeout))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection.unbind()
        }
    }

    private fun hasSecurePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun isShizukuAppInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private inner class GrantConnection : ServiceConnection {
        private val service = CompletableDeferred<IGrantService>()
        private var bound = false

        suspend fun bind(): IGrantService {
            Shizuku.bindUserService(args, this)
            bound = true
            return service.await()
        }

        fun unbind() {
            if (!bound) return
            bound = false
            // remove = true → proses UserService dihentikan
            runCatching { Shizuku.unbindUserService(args, this, true) }
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                service.complete(IGrantService.Stub.asInterface(binder))
            } else {
                service.completeExceptionally(GrantException(GrantError.ServiceUnavailable))
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service.completeExceptionally(GrantException(GrantError.ServiceUnavailable))
        }
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 1001
        const val BIND_TIMEOUT_MS = 10_000L
    }
}
