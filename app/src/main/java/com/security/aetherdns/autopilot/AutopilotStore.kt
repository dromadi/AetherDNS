package com.security.aetherdns.autopilot

import android.content.Context
import androidx.core.content.edit
import com.security.aetherdns.data.ProtectionSnapshot

/** Format satu baris TSV untuk [AutopilotHold]; baris rusak dianggap tidak ada. Diuji di `AutopilotPolicyTest`. */
object AutopilotHoldCodec {
    fun encode(hold: AutopilotHold): String = listOf(
        hold.original,
        hold.reason.name,
        hold.expected.active,
        hold.expected.specifier,
        hold.expected.paused,
        hold.since,
        hold.substitute.orEmpty()
    ).joinToString("\t") { it.toString().replace('\t', ' ') }

    fun decode(line: String?): AutopilotHold? = runCatching {
        val p = line!!.split('\t')
        AutopilotHold(
            original = p[0],
            reason = AutopilotReason.valueOf(p[1]),
            expected = ProtectionSnapshot(p[2].toBooleanStrict(), p[3], p[4].toBooleanStrict()),
            since = p[5].toLong(),
            substitute = p.getOrNull(6)?.takeIf { it.isNotEmpty() }
        )
    }.getOrNull()
}

/**
 * Sakelar Autopilot (aktif secara bawaan) dan tindakan yang sedang berlaku. Dikecualikan dari backup karena
 * hanya bermakna di perangkat dan jaringan asal.
 */
class AutopilotStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    var hold: AutopilotHold?
        get() = AutopilotHoldCodec.decode(prefs.getString(KEY_HOLD, null))
        set(value) = prefs.edit { if (value == null) remove(KEY_HOLD) else putString(KEY_HOLD, AutopilotHoldCodec.encode(value)) }

    companion object {
        const val PREFS = "autopilot"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOLD = "hold"
    }
}
