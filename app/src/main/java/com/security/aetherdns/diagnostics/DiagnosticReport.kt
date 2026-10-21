package com.security.aetherdns.diagnostics

import com.security.aetherdns.util.AuditSummary
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.NetworkCheck
import com.security.aetherdns.util.NetworkReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Info aplikasi & perangkat (tanpa data pribadi: tidak ada SSID, IP perangkat, token, atau API key). */
data class DeviceInfo(
    val appVersion: String,
    val appVersionCode: Int,
    val buildType: String,
    val packageName: String,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val locale: String
)

data class NetworkInfo(
    /** "wifi", "cellular", "ethernet", "none", ... */
    val transport: String,
    val vpnActive: Boolean,
    /** Nama operator seluler (tidak butuh izin); kosong bila tidak tersedia. */
    val carrier: String,
    val osPrivateDnsActive: Boolean,
    val osPrivateDnsServer: String?
)

/** Status fitur aplikasi; hanya boolean/ringkasan, tidak pernah nilai rahasia. */
data class FeatureStatus(
    val hasPermission: Boolean,
    val mode: String,
    val specifier: String,
    val presetName: String?,
    val protectionActive: Boolean,
    val pausedUntil: Long?,
    val nativeActivationSupported: Boolean,
    val activationState: String,
    val shizukuStatus: String,
    val notificationsAllowed: Boolean,
    val automationEnabled: Boolean,
    val nextDnsApiKeySet: Boolean,
    val nextDnsProfileDetected: Boolean,
    val savedProfiles: Int,
    val widgetsPlaced: Int,
    val onboardingCompleted: Boolean
)

data class CrashInfo(val timestamp: Long, val thread: String, val stackTrace: String)

data class DiagnosticSnapshot(
    val generatedAt: Long,
    val device: DeviceInfo,
    val network: NetworkInfo,
    val features: FeatureStatus,
    val activeResolverProbe: DotProbeResult?,
    val networkReport: NetworkReport?,
    val audit: AuditSummary?,
    val lastCrash: CrashInfo?
)

/**
 * Menyusun laporan teks polos yang mudah disalin ke chat / issue. Format sengaja stabil dan berbahasa Inggris
 * teknis (data diagnostik, bukan teks UI). Diuji di `DiagnosticReportTest`, termasuk bahwa rahasia tidak bocor.
 */
object DiagnosticReportFormatter {

    private const val MAX_STACK_LINES = 25

    fun format(s: DiagnosticSnapshot): String = buildString {
        appendLine("=== AetherDNS diagnostic report ===")
        appendLine("Generated: ${timestamp(s.generatedAt)}")
        appendLine()

        section("App & device")
        line("App", "${s.device.appVersion} (${s.device.appVersionCode}, ${s.device.buildType}) ${s.device.packageName}")
        line("Device", "${s.device.manufacturer} ${s.device.model}")
        line("Android", "${s.device.androidRelease} (SDK ${s.device.sdkInt})")
        line("Locale", s.device.locale)

        section("Network")
        line("Transport", s.network.transport + if (s.network.vpnActive) " + VPN" else "")
        line("Carrier", s.network.carrier.ifBlank { "-" })
        line("OS Private DNS active", yesNo(s.network.osPrivateDnsActive))
        line("OS Private DNS server", s.network.osPrivateDnsServer ?: "-")

        section("Protection")
        val f = s.features
        line("Permission WRITE_SECURE_SETTINGS", yesNo(f.hasPermission))
        line("Private DNS mode", f.mode)
        line("Specifier", f.specifier.ifBlank { "-" })
        line("Preset", f.presetName ?: "custom / none")
        line("Protection active", yesNo(f.protectionActive))
        line("Paused until", f.pausedUntil?.let { timestamp(it) } ?: "-")
        line(
            "Active resolver probe",
            when (val p = s.activeResolverProbe) {
                null -> "not run"
                is DotProbeResult.Success -> "OK (handshake ${p.handshakeMs} ms, query ${p.queryMs} ms)"
                is DotProbeResult.Failure -> "FAILED ${p.cause}${p.detail?.let { " ($it)" }.orEmpty()}"
            }
        )

        section("Activation")
        line("Built-in activation supported", yesNo(f.nativeActivationSupported))
        line("Built-in activation state", f.activationState)
        line("Shizuku", f.shizukuStatus)
        line("Notifications allowed", yesNo(f.notificationsAllowed))

        section("Network check")
        val report = s.networkReport
        if (report == null) {
            appendLine("not run")
        } else {
            NetworkCheck.entries.forEach { line(it.name, report[it].name) }
            line("Plain DNS tampered", yesNo(report.isPlainDnsTampered))
        }

        section("Ad audit (system DNS)")
        val audit = s.audit
        when {
            audit == null -> appendLine("not run")
            !audit.isNetworkReachable -> appendLine("offline: control domain did not resolve")
            else -> {
                line("Blocked", "${audit.blockedCount}/${audit.totalCount} (phone cache: ${audit.cachedCount})")
                audit.items.forEach { item ->
                    line(item.domain, item.outcome.name + item.detail.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty())
                }
            }
        }

        section("Other features")
        line("Automation (Tasker)", if (f.automationEnabled) "enabled" else "disabled")
        line("NextDNS stats", "api key ${if (f.nextDnsApiKeySet) "set" else "not set"}, profile ${if (f.nextDnsProfileDetected) "detected" else "not detected"}")
        line("Saved profiles", f.savedProfiles.toString())
        line("Widgets placed", f.widgetsPlaced.toString())
        line("Onboarding completed", yesNo(f.onboardingCompleted))

        section("Last crash")
        val crash = s.lastCrash
        if (crash == null) {
            appendLine("none recorded")
        } else {
            line("When", timestamp(crash.timestamp))
            line("Thread", crash.thread)
            crash.stackTrace.lineSequence().take(MAX_STACK_LINES).forEach { appendLine(it) }
        }
        appendLine()
        append("=== end ===")
    }

    private fun StringBuilder.section(title: String) {
        appendLine()
        appendLine("## $title")
    }

    private fun StringBuilder.line(key: String, value: String) {
        appendLine("$key: $value")
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    private fun timestamp(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMs))

}
