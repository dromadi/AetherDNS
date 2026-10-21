package com.security.aetherdns.data

import com.security.aetherdns.util.CheckResult
import java.io.File
import kotlin.math.roundToLong

/** Jenis jaringan saat sampel diambil; nama Wi-Fi sengaja tidak dicatat (butuh izin lokasi, data pribadi). */
data class NetworkKey(val transport: String, val carrier: String) {
    /** Operator hanya bermakna untuk data seluler. */
    val normalized: NetworkKey get() = if (transport == "cellular") this else copy(carrier = "")
}

sealed class InsightSample {
    abstract val timestamp: Long
    abstract val network: NetworkKey

    /**
     * Latensi DoT; [totalMs] null bila probe gagal. [benchmark] = diukur saat "Uji Kecepatan" (resolver belum
     * tentu dipakai), hanya dipakai untuk mencari alternatif yang lebih cepat.
     */
    data class Latency(
        override val timestamp: Long,
        override val network: NetworkKey,
        val resolver: String,
        val totalMs: Long?,
        val benchmark: Boolean = false
    ) : InsightSample()

    data class NetworkCheck(
        override val timestamp: Long,
        override val network: NetworkKey,
        val interception: CheckResult,
        val hijack: CheckResult,
        val dot: CheckResult
    ) : InsightSample() {
        val tampered: Boolean get() = interception == CheckResult.PROBLEM || hijack == CheckResult.PROBLEM
    }
}

/**
 * Format baris TSV sederhana, tahan terhadap baris rusak (dilewati). Diuji di `InsightsTest`.
 * L<TAB>waktu<TAB>transport<TAB>operator<TAB>resolver<TAB>ms|-[<TAB>b]
 * N<TAB>waktu<TAB>transport<TAB>operator<TAB>interception<TAB>hijack<TAB>dot
 */
object InsightCodec {
    fun encode(sample: InsightSample): String {
        val net = "${clean(sample.network.transport)}\t${clean(sample.network.carrier)}"
        return when (sample) {
            is InsightSample.Latency -> "L\t${sample.timestamp}\t$net\t${clean(sample.resolver)}\t${sample.totalMs ?: "-"}" +
                if (sample.benchmark) "\tb" else ""
            is InsightSample.NetworkCheck -> "N\t${sample.timestamp}\t$net\t${sample.interception}\t${sample.hijack}\t${sample.dot}"
        }
    }

    fun decode(line: String): InsightSample? = runCatching {
        val p = line.split('\t')
        val network = NetworkKey(p[2], p[3])
        when (p[0]) {
            "L" -> InsightSample.Latency(p[1].toLong(), network, p[4], p[5].toLongOrNull(), p.getOrNull(6) == "b")
            "N" -> InsightSample.NetworkCheck(
                p[1].toLong(), network,
                CheckResult.valueOf(p[4]), CheckResult.valueOf(p[5]), CheckResult.valueOf(p[6])
            )
            else -> null
        }
    }.getOrNull()

    private fun clean(value: String) = value.replace('\t', ' ').replace('\n', ' ')
}

fun interface InsightStore {
    fun append(sample: InsightSample)
    fun all(): List<InsightSample> = emptyList()
    fun clear() {}
}

/** Disimpan di `noBackupFilesDir` (tidak ikut backup), dipangkas ke [MAX_AGE_MS] dan [MAX_SAMPLES]. */
class FileInsightStore(dir: File, private val now: () -> Long = System::currentTimeMillis) : InsightStore {
    private val file = File(dir, FILE_NAME)

    @Synchronized
    override fun append(sample: InsightSample) {
        val kept = all().filter { now() - it.timestamp <= MAX_AGE_MS }.takeLast(MAX_SAMPLES - 1) + sample
        runCatching { file.writeText(kept.joinToString("\n") { InsightCodec.encode(it) }) }
    }

    @Synchronized
    override fun all(): List<InsightSample> = runCatching {
        if (!file.exists()) emptyList() else file.readLines().mapNotNull { InsightCodec.decode(it) }
    }.getOrDefault(emptyList())

    @Synchronized
    override fun clear() {
        runCatching { file.delete() }
    }

    companion object {
        const val FILE_NAME = "insights.tsv"
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_SAMPLES = 1000
    }
}

enum class SpeedLevel { FAST, OK, SLOW }

data class LatencySummary(
    val resolver: String,
    val network: NetworkKey,
    val medianMs: Long,
    val samples: Int,
    val failures: Int,
    /** Median per hari (paling lama → terbaru), untuk grafik batang. */
    val daily: List<Long>
) {
    val level: SpeedLevel
        get() = when {
            medianMs < InsightAnalyzer.FAST_MS -> SpeedLevel.FAST
            medianMs < InsightAnalyzer.SLOW_MS -> SpeedLevel.OK
            else -> SpeedLevel.SLOW
        }
}

/** Resolver sekelompok yang jauh lebih cepat di jaringan yang sama. */
data class FasterAlternative(val resolver: String, val medianMs: Long, val currentMs: Long) {
    val factor: Int get() = (currentMs / medianMs.coerceAtLeast(1)).toInt()
}

data class NetworkSummary(val network: NetworkKey, val checks: Int, val tampered: Int, val dotBlocked: Int)

/** Agregasi murni untuk halaman Insight (diuji di `InsightsTest`). */
object InsightAnalyzer {
    const val FAST_MS = 150L
    const val SLOW_MS = 400L
    /** Minimal sampel sebelum kecepatan dinilai, agar satu pengukuran buruk tidak memicu saran. */
    const val MIN_SAMPLES = 3
    /** Alternatif baru disarankan bila minimal sekian kali lebih cepat. */
    const val MIN_SPEEDUP = 2
    const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Ringkasan per resolver × jaringan. Hanya resolver yang pernah aktif yang ditampilkan; sampel uji kecepatan
     * ikut menyumbang median-nya.
     */
    fun latency(samples: List<InsightSample>, now: Long, windowMs: Long = 7 * DAY_MS): List<LatencySummary> =
        allLatency(samples, now, windowMs).filter { it.first }.map { it.second }

    /**
     * Saran resolver lain dari [candidates] (mis. satu kelompok pemblokiran) yang ≥ [MIN_SPEEDUP]× lebih cepat
     * daripada [current] yang lambat, di jaringan yang sama.
     */
    fun fasterAlternative(
        samples: List<InsightSample>,
        current: LatencySummary,
        candidates: Set<String>,
        now: Long
    ): FasterAlternative? {
        if (current.level != SpeedLevel.SLOW) return null
        val wanted = candidates.map { it.lowercase() }.toSet()
        return allLatency(samples, now, 7 * DAY_MS)
            .map { it.second }
            .filter {
                it.network == current.network &&
                    it.resolver.lowercase() in wanted &&
                    !it.resolver.equals(current.resolver, ignoreCase = true) &&
                    it.medianMs * MIN_SPEEDUP <= current.medianMs
            }
            .minByOrNull { it.medianMs }
            ?.let { FasterAlternative(it.resolver, it.medianMs, current.medianMs) }
    }

    /** (pernah aktif?, ringkasan) */
    private fun allLatency(samples: List<InsightSample>, now: Long, windowMs: Long): List<Pair<Boolean, LatencySummary>> =
        samples.filterIsInstance<InsightSample.Latency>()
            .filter { now - it.timestamp <= windowMs }
            .groupBy { it.resolver to it.network.normalized }
            .mapNotNull { (key, group) ->
                val ok = group.mapNotNull { it.totalMs }
                if (ok.isEmpty()) return@mapNotNull null
                val daily = group.filter { it.totalMs != null }
                    .groupBy { it.timestamp / DAY_MS }
                    .toSortedMap()
                    .values
                    .map { day -> median(day.mapNotNull { it.totalMs }) }
                val everActive = group.any { !it.benchmark }
                everActive to LatencySummary(key.first, key.second, median(ok), ok.size, group.size - ok.size, daily)
            }
            .sortedByDescending { it.second.samples }

    fun networks(samples: List<InsightSample>, now: Long, windowMs: Long = 30 * DAY_MS): List<NetworkSummary> =
        samples.filterIsInstance<InsightSample.NetworkCheck>()
            .filter { now - it.timestamp <= windowMs }
            .groupBy { it.network.normalized }
            .map { (network, group) ->
                NetworkSummary(
                    network,
                    checks = group.size,
                    tampered = group.count { it.tampered },
                    dotBlocked = group.count { it.dot == CheckResult.PROBLEM }
                )
            }
            .sortedByDescending { it.checks }

    /** Ringkasan resolver aktif di jaringan saat ini, hanya bila sampel cukup. */
    fun current(samples: List<InsightSample>, resolver: String, network: NetworkKey, now: Long): LatencySummary? =
        latency(samples, now).firstOrNull { it.resolver == resolver && it.network == network.normalized && it.samples >= MIN_SAMPLES }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else ((sorted[mid - 1] + sorted[mid]) / 2.0).roundToLong()
    }
}
