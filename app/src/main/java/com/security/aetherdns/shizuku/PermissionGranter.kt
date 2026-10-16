package com.security.aetherdns.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.security.aetherdns.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

class ShizukuGranter(private val context: Context) : PermissionGranter {

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(context, GrantService::class.java))
            .daemon(false) // Proses mati otomatis setelah unbind -> 0% overhead
            .processNameSuffix("grant")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    override fun status(): GranterStatus {
        val isInstalled = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

        if (!isInstalled) return GranterStatus.NOT_INSTALLED
        if (!Shizuku.pingBinder()) return GranterStatus.NOT_RUNNING
        if (Shizuku.isPreV11()) return GranterStatus.UNSUPPORTED

        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            GranterStatus.READY
        } else {
            GranterStatus.NEEDS_AUTHORIZATION
        }
    }

    override suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { cont ->
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }

        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }

        Shizuku.addRequestPermissionResultListener(listener)
        cont.invokeOnCancellation {
            Shizuku.removeRequestPermissionResultListener(listener)
        }

        Shizuku.requestPermission(1001)
    }

    override suspend fun grant(): Result<Unit> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val exitCode = runCatching {
                        val service = IGrantService.Stub.asInterface(binder)
                        service.grantSecureSettings()
                    }

                    // Lepas dan hentikan UserService segera
                    try {
                        Shizuku.unbindUserService(args, this, true)
                    } catch (_: Exception) {}

                    val result = exitCode.fold(
                        onSuccess = { code ->
                            if (code == 0) {
                                Result.success(Unit)
                            } else {
                                Result.failure(Exception("pm grant gagal (exit $code). Jika di MIUI/HyperOS, aktifkan 'USB debugging (Security settings)' di Opsi Pengembang."))
                            }
                        },
                        onFailure = { Result.failure(it) }
                    )
                    cont.resume(result)
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }

            try {
                Shizuku.bindUserService(args, conn)
            } catch (e: Exception) {
                cont.resume(Result.failure(e))
            }

            cont.invokeOnCancellation {
                try {
                    Shizuku.unbindUserService(args, conn, true)
                } catch (_: Exception) {}
            }
        }
    }
}
