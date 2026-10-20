package com.security.aetherdns.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.security.aetherdns.R
import com.security.aetherdns.data.ApplyError
import com.security.aetherdns.data.PauseError
import com.security.aetherdns.data.ProfileError
import com.security.aetherdns.model.DnsProfile
import com.security.aetherdns.shizuku.GrantError
import com.security.aetherdns.shizuku.GrantException
import com.security.aetherdns.util.DotProbeCause
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.HostnameError
import com.security.aetherdns.util.NextDnsHostname

/**
 * Teks yang dilokalkan saat ditampilkan, sehingga ViewModel dan lapisan data tidak bergantung pada bahasa.
 * Argumen boleh berupa [UiText] lain (di-resolve secara rekursif).
 */
sealed class UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText()
    data class Raw(val text: String) : UiText()

    fun resolve(context: Context): String = when (this) {
        is Raw -> text
        is Res -> context.getString(id, *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
    }

    @Composable
    fun resolve(): String = resolve(LocalContext.current)

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

fun HostnameError.toUiText(): UiText = UiText.of(
    when (this) {
        HostnameError.EMPTY -> R.string.hostname_error_empty
        HostnameError.URL_SCHEME -> R.string.hostname_error_url_scheme
        HostnameError.PATH_OR_QUERY -> R.string.hostname_error_path
        HostnameError.PORT_OR_IPV6 -> R.string.hostname_error_port
        HostnameError.IP_ADDRESS -> R.string.hostname_error_ip
        HostnameError.INVALID_CHARACTERS -> R.string.hostname_error_characters
        HostnameError.TOO_LONG -> R.string.hostname_error_too_long
        HostnameError.MISSING_DOT -> R.string.hostname_error_missing_dot
        HostnameError.INVALID_LABEL -> R.string.hostname_error_label
        HostnameError.INVALID_TLD -> R.string.hostname_error_tld
    }
)

fun DotProbeResult.Failure.toUiText(): UiText {
    val base = when (cause) {
        DotProbeCause.RESOLVE -> R.string.probe_error_resolve
        DotProbeCause.TIMEOUT -> R.string.probe_error_timeout
        DotProbeCause.TLS -> R.string.probe_error_tls
        DotProbeCause.CERT_UNTRUSTED -> R.string.probe_error_cert_untrusted
        DotProbeCause.CERT_MISMATCH -> R.string.probe_error_cert_mismatch
        DotProbeCause.DNS_QUERY -> R.string.probe_error_dns_query
        DotProbeCause.DNS_REFUSED -> R.string.probe_error_dns_refused
        DotProbeCause.INVALID_INPUT -> R.string.probe_error_invalid_input
        DotProbeCause.OTHER -> R.string.probe_error_other
    }
    val detail = detail?.takeIf { it.isNotBlank() } ?: return UiText.of(base)
    return UiText.of(R.string.text_with_detail, UiText.of(base), detail)
}

fun ApplyError.toUiText(): UiText = when (this) {
    ApplyError.NoPermission -> UiText.of(R.string.error_no_permission)
    is ApplyError.InvalidHostname -> error.toUiText()
    is ApplyError.ProbeFailed -> UiText.of(R.string.apply_error_probe_failed, failure.toUiText())
    ApplyError.WriteRejected -> UiText.of(R.string.apply_error_write_rejected)
}

fun PauseError.toUiText(): UiText = UiText.of(
    when (this) {
        PauseError.NO_PERMISSION -> R.string.error_no_permission
        PauseError.NOT_ACTIVE -> R.string.pause_error_not_active
        PauseError.WRITE_REJECTED -> R.string.pause_error_write_rejected
    }
)

fun ProfileError.toUiText(): UiText = when (this) {
    is ProfileError.InvalidHostname -> error.toUiText()
    ProfileError.Duplicate -> UiText.of(R.string.profile_error_duplicate)
    is ProfileError.LimitReached -> UiText.of(R.string.profile_error_limit, max)
}

fun NextDnsHostname.Error.toUiText(): UiText = UiText.of(
    when (this) {
        NextDnsHostname.Error.INVALID_CONFIG_ID -> R.string.nextdns_error_config_id
        NextDnsHostname.Error.DEVICE_NAME_TOO_LONG -> R.string.nextdns_error_device_too_long
    }
)

fun Throwable.toGrantUiText(): UiText = when (val error = (this as? GrantException)?.error) {
    GrantError.OemRestricted -> UiText.of(R.string.grant_error_oem)
    is GrantError.PmFailed ->
        if (error.firstOutputLine != null) UiText.of(R.string.grant_error_pm_output, error.exitCode, error.firstOutputLine)
        else UiText.of(R.string.grant_error_pm, error.exitCode)
    GrantError.Timeout -> UiText.of(R.string.grant_error_timeout)
    GrantError.ServiceUnavailable -> UiText.of(R.string.grant_error_service)
    null -> UiText.of(R.string.grant_error_generic, message ?: javaClass.simpleName)
}

/** Nama profil: resource terlokalisasi untuk preset, nama ketikan pengguna untuk profil tersimpan. */
fun DnsProfile.displayName(context: Context): String =
    if (nameRes != 0) context.getString(nameRes) else name

@Composable
fun DnsProfile.displayName(): String = displayName(LocalContext.current)
