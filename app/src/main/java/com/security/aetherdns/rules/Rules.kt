package com.security.aetherdns.rules

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Jenis jaringan yang bisa dibedakan tanpa izin lokasi (nama Wi-Fi sengaja tidak dipakai). */
enum class NetworkType { WIFI, CELLULAR, OTHER }

/** Tujuan sebuah aturan: resolver tertentu, atau resolver tercepat sekelompok dengan pilihan utama. */
sealed class RuleTarget {
    data class Resolver(val hostname: String) : RuleTarget()
    data object Fastest : RuleTarget()
}

/**
 * Jadwal mingguan. [startMinute] / [endMinute] = menit sejak tengah malam; bila end ≤ start jadwal melewati
 * tengah malam (mis. 21:00–06:00) dan [days] adalah hari mulainya.
 */
data class ScheduleRule(
    val id: String,
    val name: String,
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val hostname: String
) {
    private val crossesMidnight: Boolean get() = endMinute <= startMinute

    fun isActive(now: LocalDateTime): Boolean {
        val minute = now.hour * 60 + now.minute
        val today = now.dayOfWeek
        return if (!crossesMidnight) {
            today in days && minute in startMinute until endMinute
        } else {
            (today in days && minute >= startMinute) || (today.minus(1) in days && minute < endMinute)
        }
    }
}

data class RuleSet(
    val enabled: Boolean = false,
    val wifi: RuleTarget? = null,
    val cellular: RuleTarget? = null,
    val schedules: List<ScheduleRule> = emptyList()
) {
    val isEmpty: Boolean get() = wifi == null && cellular == null && schedules.isEmpty()
}

/** Asal keputusan, untuk catatan di kartu status dan halaman Aturan. */
sealed class RuleSource {
    data class Schedule(val rule: ScheduleRule) : RuleSource()
    data class Network(val type: NetworkType) : RuleSource()
}

data class RuleDecision(val target: RuleTarget, val source: RuleSource)

/** Logika murni Aturan Pintar (diuji di `RulesTest`). Jadwal mengalahkan aturan jaringan. */
object RuleEngine {

    /** @return null bila tidak ada aturan yang berlaku: resolver pilihan utama dipakai. */
    fun decide(rules: RuleSet, network: NetworkType, now: LocalDateTime): RuleDecision? {
        if (!rules.enabled) return null
        rules.schedules.firstOrNull { it.isActive(now) }?.let {
            return RuleDecision(RuleTarget.Resolver(it.hostname), RuleSource.Schedule(it))
        }
        val byNetwork = when (network) {
            NetworkType.WIFI -> rules.wifi
            NetworkType.CELLULAR -> rules.cellular
            NetworkType.OTHER -> null
        } ?: return null
        return RuleDecision(byNetwork, RuleSource.Network(network))
    }

    /** Batas jadwal berikutnya (mulai/selesai) setelah [now], untuk satu alarm; null bila tidak ada jadwal. */
    fun nextBoundary(rules: RuleSet, now: LocalDateTime): LocalDateTime? {
        if (!rules.enabled || rules.schedules.isEmpty()) return null
        val base = now.withSecond(0).withNano(0)
        return rules.schedules.flatMap { rule ->
            (0..7).flatMap { offset ->
                val day = base.toLocalDate().plusDays(offset.toLong())
                listOf(rule.startMinute, rule.endMinute).map { minute ->
                    day.atStartOfDay().plusMinutes(minute.toLong())
                }
            }
        }.filter { it.isAfter(now) }.minOrNull()
    }
}

/** Serialisasi JSON (org.json bawaan Android); data rusak menghasilkan [RuleSet] kosong. */
object RulesCodec {
    fun encode(rules: RuleSet): String = JSONObject().apply {
        put("enabled", rules.enabled)
        rules.wifi?.let { put("wifi", encodeTarget(it)) }
        rules.cellular?.let { put("cellular", encodeTarget(it)) }
        put("schedules", JSONArray().apply {
            rules.schedules.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("days", JSONArray(s.days.map { it.value }.sorted()))
                    put("start", s.startMinute)
                    put("end", s.endMinute)
                    put("hostname", s.hostname)
                })
            }
        })
    }.toString()

    fun decode(json: String?): RuleSet = runCatching {
        val o = JSONObject(json!!)
        val schedules = o.optJSONArray("schedules")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val s = arr.getJSONObject(i)
                    val days = s.getJSONArray("days").let { d -> (0 until d.length()).map { DayOfWeek.of(d.getInt(it)) }.toSet() }
                    ScheduleRule(
                        s.getString("id"), s.optString("name"), days,
                        s.getInt("start").coerceIn(0, 1439), s.getInt("end").coerceIn(0, 1439), s.getString("hostname")
                    )
                }.getOrNull()
            }
        }.orEmpty()
        RuleSet(
            enabled = o.optBoolean("enabled", false),
            wifi = decodeTarget(o.optString("wifi", "")),
            cellular = decodeTarget(o.optString("cellular", "")),
            schedules = schedules
        )
    }.getOrDefault(RuleSet())

    private fun encodeTarget(target: RuleTarget): String = when (target) {
        RuleTarget.Fastest -> FASTEST
        is RuleTarget.Resolver -> target.hostname
    }

    private fun decodeTarget(value: String): RuleTarget? = when {
        value.isBlank() -> null
        value == FASTEST -> RuleTarget.Fastest
        else -> RuleTarget.Resolver(value)
    }

    private const val FASTEST = "@fastest"
}
