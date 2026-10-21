package com.security.aetherdns.util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.ceil

enum class StabilityVerdict { STABLE, SLOW, UNSTABLE }

/**
 * Hasil uji stabilitas satu resolver: [latencies] berisi waktu (ms) tiap percobaan yang berhasil,
 * [failures] jumlah percobaan gagal (timeout, TLS, query).
 */
data class StabilityResult(val resolver: String, val latencies: List<Long>, val failures: Int) {
    val attempts: Int get() = latencies.size + failures
    val medianMs: Long? get() = percentile(50)
    val worstMs: Long? get() = latencies.maxOrNull()

    val verdict: StabilityVerdict
        get() {
            val median = medianMs ?: return StabilityVerdict.UNSTABLE
            val p90 = percentile(90) ?: median
            return when {
                // Satu kegagalan dari belasan percobaan sudah cukup terasa sebagai "putus sebentar"
                failures > 0 || p90 >= StabilityTest.UNSTABLE_MS -> StabilityVerdict.UNSTABLE
                median >= StabilityTest.SLOW_MS -> StabilityVerdict.SLOW
                else -> StabilityVerdict.STABLE
            }
        }

    private fun percentile(p: Int): Long? {
        if (latencies.isEmpty()) return null
        val sorted = latencies.sorted()
        val index = (ceil(p / 100.0 * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}

/**
 * Mengukur beberapa resolver berulang kali secara bersamaan (koneksi TLS baru setiap kali, seperti Android
 * setelah koneksi DoT-nya terputus). Diuji di `StabilityTestTest` dengan prober palsu.
 */
class StabilityTest(
    private val prober: DotProber,
    private val rounds: Int = ROUNDS,
    private val intervalMs: Long = INTERVAL_MS,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    /** @param onProgress dipanggil setelah setiap putaran selesai (1..[rounds]). */
    suspend fun run(resolvers: List<String>, onProgress: (Int) -> Unit = {}): List<StabilityResult> = coroutineScope {
        val latencies = resolvers.associateWith { mutableListOf<Long>() }
        val failures = resolvers.associateWith { 0 }.toMutableMap()
        for (round in 1..rounds) {
            val results = resolvers.map { host -> async { host to prober.probe(host) } }.awaitAll()
            for ((host, result) in results) {
                when (result) {
                    is DotProbeResult.Success -> latencies.getValue(host) += result.totalMs
                    is DotProbeResult.Failure -> failures[host] = failures.getValue(host) + 1
                }
            }
            onProgress(round)
            if (round < rounds) pause(intervalMs)
        }
        resolvers.map { StabilityResult(it, latencies.getValue(it).toList(), failures.getValue(it)) }
    }

    companion object {
        const val ROUNDS = 12
        const val INTERVAL_MS = 2_500L
        const val SLOW_MS = 400L
        const val UNSTABLE_MS = 1_500L
    }
}

/** Kesimpulan uji stabilitas dalam bahasa keputusan, bukan angka. Diuji di `StabilityTestTest`. */
sealed class StabilityAdvice {
    /** Resolver aktif stabil: putus-nyambung (bila ada) bukan karena resolver. */
    data object ActiveStable : StabilityAdvice()

    /** Pembanding pun tidak stabil: jaringannya yang bermasalah. */
    data object NetworkProblem : StabilityAdvice()

    /** Resolver sekelompok yang stabil dan tercepat. */
    data class SwitchTo(val resolver: String, val medianMs: Long) : StabilityAdvice()

    /** Resolver aktif bermasalah, tetapi tidak ada alternatif sekelompok yang stabil. */
    data object NoAlternative : StabilityAdvice()

    companion object {
        fun of(results: List<StabilityResult>, active: String, alternatives: Set<String>, reference: String): StabilityAdvice {
            val byHost = results.associateBy { it.resolver.lowercase() }
            val current = byHost[active.lowercase()] ?: return NoAlternative
            if (current.verdict == StabilityVerdict.STABLE) return ActiveStable
            val ref = byHost[reference.lowercase()]
            if (ref != null && ref.verdict == StabilityVerdict.UNSTABLE && !active.equals(reference, ignoreCase = true)) {
                return NetworkProblem
            }
            val best = alternatives.mapNotNull { byHost[it.lowercase()] }
                .filter { !it.resolver.equals(active, ignoreCase = true) && it.verdict == StabilityVerdict.STABLE }
                .minByOrNull { it.medianMs ?: Long.MAX_VALUE }
            return best?.let { SwitchTo(it.resolver, it.medianMs ?: 0) } ?: NoAlternative
        }
    }
}
