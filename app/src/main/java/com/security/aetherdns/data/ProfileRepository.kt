package com.security.aetherdns.data

import android.content.Context
import com.security.aetherdns.model.DnsProfile
import com.security.aetherdns.util.HostnameValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** Penyimpanan teks mentah daftar profil kustom. */
interface ProfileStore {
    fun read(): String?
    fun write(encoded: String)
}

class SharedPrefsProfileStore(context: Context) : ProfileStore {
    private val prefs = context.getSharedPreferences("custom_profiles", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString(KEY, null)
    override fun write(encoded: String) = prefs.edit().putString(KEY, encoded).apply()

    private companion object {
        const val KEY = "profiles"
    }
}

/** Format baris: `id<TAB>nama<TAB>hostname`. Tab/baris baru pada nama dibuang saat encode. */
object ProfileCodec {
    fun encode(profiles: List<DnsProfile>): String =
        profiles.joinToString("\n") { p ->
            listOf(p.id, p.name.replace(Regex("[\\t\\r\\n]"), " ").trim(), p.hostname).joinToString("\t")
        }

    fun decode(encoded: String?): List<DnsProfile> =
        encoded.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            val (id, name, hostname) = parts
            val valid = HostnameValidator.validate(hostname) as? HostnameValidator.Result.Valid ?: return@mapNotNull null
            customProfile(id, name, valid.fqdn)
        }.toList()

    fun customProfile(id: String, name: String, hostname: String) = DnsProfile(
        id = id,
        name = name,
        hostname = hostname,
        category = "Profil Tersimpan",
        description = "",
        isCustom = true
    )
}

sealed class ProfileSaveResult {
    data class Saved(val profile: DnsProfile) : ProfileSaveResult()
    data class Failure(val reason: String) : ProfileSaveResult()
}

class ProfileRepository(private val store: ProfileStore) {
    private val _profiles = MutableStateFlow(ProfileCodec.decode(store.read()))
    val profiles: StateFlow<List<DnsProfile>> = _profiles.asStateFlow()

    fun add(name: String, hostname: String): ProfileSaveResult {
        val validation = HostnameValidator.validate(hostname)
        if (validation is HostnameValidator.Result.Invalid) return ProfileSaveResult.Failure(validation.reason)
        val fqdn = (validation as HostnameValidator.Result.Valid).fqdn

        val current = _profiles.value
        if (current.any { it.hostname == fqdn }) return ProfileSaveResult.Failure("Hostname ini sudah tersimpan")
        if (current.size >= MAX_PROFILES) return ProfileSaveResult.Failure("Maksimal $MAX_PROFILES profil tersimpan")

        val displayName = name.trim().ifEmpty { fqdn }.take(MAX_NAME_LENGTH)
        val profile = ProfileCodec.customProfile("saved_${UUID.randomUUID()}", displayName, fqdn)
        persist(current + profile)
        return ProfileSaveResult.Saved(profile)
    }

    fun remove(id: String) = persist(_profiles.value.filterNot { it.id == id })

    private fun persist(list: List<DnsProfile>) {
        _profiles.update { list }
        store.write(ProfileCodec.encode(list))
    }

    private companion object {
        const val MAX_PROFILES = 20
        const val MAX_NAME_LENGTH = 40
    }
}
