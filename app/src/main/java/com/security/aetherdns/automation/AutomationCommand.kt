package com.security.aetherdns.automation

import com.security.aetherdns.model.DnsProfile
import java.security.MessageDigest

/** Perintah dari aplikasi otomasi (Tasker, MacroDroid, Automate) melalui broadcast intent. */
sealed class AutomationCommand {
    data object Enable : AutomationCommand()
    data object Disable : AutomationCommand()
    data class Pause(val minutes: Int) : AutomationCommand()
    /** Menerapkan resolver baru (dengan probe DoT). */
    data class Apply(val hostname: String) : AutomationCommand()
}

sealed class AutomationRejection {
    data object BadToken : AutomationRejection()
    data object UnknownAction : AutomationRejection()
    data object MissingTarget : AutomationRejection()
    data class UnknownProfile(val name: String) : AutomationRejection()
    data class InvalidMinutes(val value: String) : AutomationRejection()
}

object AutomationProtocol {
    const val ACTION_ENABLE = "com.security.aetherdns.action.ENABLE"
    const val ACTION_DISABLE = "com.security.aetherdns.action.DISABLE"
    const val ACTION_PAUSE = "com.security.aetherdns.action.PAUSE"
    const val ACTION_APPLY = "com.security.aetherdns.action.APPLY"

    const val EXTRA_TOKEN = "token"
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_HOSTNAME = "hostname"
    const val EXTRA_PROFILE = "profile"

    const val DEFAULT_PAUSE_MINUTES = 15
    const val MAX_PAUSE_MINUTES = 24 * 60

    /**
     * Memvalidasi token lalu menerjemahkan action + extras menjadi perintah.
     * [extras] berisi nilai extra sebagai String (Tasker/MacroDroid mengirim semua extra sebagai teks).
     * [profiles]: preset bawaan + profil tersimpan; extra `profile` dicocokkan ke ID atau nama (tanpa beda huruf besar/kecil).
     */
    fun parse(
        action: String?,
        extras: Map<String, String?>,
        expectedToken: String,
        profiles: List<DnsProfile>,
        profileName: (DnsProfile) -> String
    ): Result {
        if (!tokenMatches(extras[EXTRA_TOKEN], expectedToken)) return Result.Rejected(AutomationRejection.BadToken)

        val command = when (action) {
            ACTION_ENABLE -> AutomationCommand.Enable
            ACTION_DISABLE -> AutomationCommand.Disable
            ACTION_PAUSE -> {
                val raw = extras[EXTRA_MINUTES]?.trim()
                val minutes = if (raw.isNullOrEmpty()) DEFAULT_PAUSE_MINUTES else raw.toIntOrNull()
                if (minutes == null || minutes !in 1..MAX_PAUSE_MINUTES) {
                    return Result.Rejected(AutomationRejection.InvalidMinutes(raw.orEmpty()))
                }
                AutomationCommand.Pause(minutes)
            }
            ACTION_APPLY -> {
                val hostname = extras[EXTRA_HOSTNAME]?.trim().orEmpty()
                val profile = extras[EXTRA_PROFILE]?.trim().orEmpty()
                when {
                    hostname.isNotEmpty() -> AutomationCommand.Apply(hostname)
                    profile.isNotEmpty() -> {
                        val match = profiles.firstOrNull {
                            it.id.equals(profile, ignoreCase = true) || profileName(it).equals(profile, ignoreCase = true)
                        } ?: return Result.Rejected(AutomationRejection.UnknownProfile(profile))
                        AutomationCommand.Apply(match.hostname)
                    }
                    else -> return Result.Rejected(AutomationRejection.MissingTarget)
                }
            }
            else -> return Result.Rejected(AutomationRejection.UnknownAction)
        }
        return Result.Accepted(command)
    }

    /** Perbandingan waktu-konstan; token kosong selalu ditolak. */
    fun tokenMatches(provided: String?, expected: String): Boolean {
        if (provided.isNullOrEmpty() || expected.isEmpty()) return false
        return MessageDigest.isEqual(provided.trim().toByteArray(), expected.toByteArray())
    }

    sealed class Result {
        data class Accepted(val command: AutomationCommand) : Result()
        data class Rejected(val reason: AutomationRejection) : Result()
    }
}
