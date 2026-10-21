package com.security.aetherdns.diagnostics

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import com.security.aetherdns.BuildConfig
import com.security.aetherdns.util.DnsManager
import com.security.aetherdns.widget.StatusWidget
import java.util.Locale

/** Mengumpulkan info platform untuk laporan diagnostik (tanpa izin tambahan, tanpa data pribadi). */
class DiagnosticsCollector(private val context: Context, val crashRecorder: CrashRecorder) {

    fun device() = DeviceInfo(
        appVersion = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
        buildType = BuildConfig.BUILD_TYPE,
        packageName = context.packageName,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        locale = Locale.getDefault().toLanguageTag()
    )

    fun network(): NetworkInfo {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val transport = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val carrier = runCatching {
            context.getSystemService(TelephonyManager::class.java)?.networkOperatorName.orEmpty()
        }.getOrDefault("")
        val (privateDnsActive, server) = DnsManager.getSystemLinkPropertiesStatus(context)
        return NetworkInfo(
            transport = transport,
            vpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            carrier = carrier,
            osPrivateDnsActive = privateDnsActive,
            osPrivateDnsServer = server
        )
    }

    fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun widgetsPlaced(): Int = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, StatusWidget::class.java)).size
    }.getOrDefault(0)
}
