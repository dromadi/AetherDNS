package com.security.aetherdns.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Ringkasan analitik NextDNS untuk satu profil dalam 24 jam terakhir. */
data class NextDnsSummary(
    val totalQueries: Long,
    val blockedQueries: Long,
    val topBlocked: List<Pair<String, Long>>
) {
    val blockedPercent: Int get() = if (totalQueries == 0L) 0 else ((blockedQueries * 100) / totalQueries).toInt()
}

/** Penyebab gagal mengambil statistik; teks dipetakan di `ui/UiText.kt`. */
enum class StatsError { INVALID_KEY, PROFILE_NOT_FOUND, NETWORK, SERVER }

sealed class StatsResult {
    data class Success(val summary: NextDnsSummary) : StatsResult()
    data class Failure(val error: StatsError) : StatsResult()
}

/** Respons HTTP mentah; diabstraksi agar klien dapat diuji di JVM. */
data class HttpResponse(val code: Int, val body: String)

fun interface HttpGet {
    /** @return null bila jaringan gagal (timeout, DNS, TLS). */
    fun get(url: String, headers: Map<String, String>): HttpResponse?
}

/** Parser JSON API NextDNS (`/analytics/status`, `/analytics/domains`). */
object NextDnsApiParser {
    /** @return (total, diblokir) dari `{"data":[{"status":"default","queries":N},{"status":"blocked",...}]}`. */
    fun parseStatus(json: String): Pair<Long, Long> {
        val data = JSONObject(json).getJSONArray("data")
        var total = 0L
        var blocked = 0L
        for (i in 0 until data.length()) {
            val row = data.getJSONObject(i)
            val queries = row.optLong("queries", 0)
            total += queries
            if (row.optString("status") == "blocked") blocked += queries
        }
        return total to blocked
    }

    fun parseDomains(json: String): List<Pair<String, Long>> {
        val data = JSONObject(json).getJSONArray("data")
        return (0 until data.length()).map { i ->
            val row = data.getJSONObject(i)
            row.getString("domain") to row.optLong("queries", 0)
        }
    }
}

class NextDnsClient(private val http: HttpGet = UrlConnectionHttpGet) {

    suspend fun summary(profileId: String, apiKey: String): StatsResult = withContext(Dispatchers.IO) {
        val headers = mapOf("X-Api-Key" to apiKey, "Accept" to "application/json")
        val profile = URLEncoder.encode(profileId, "UTF-8")
        val base = "$API/profiles/$profile/analytics"

        val status = http.get("$base/status?from=-24h", headers) ?: return@withContext StatsResult.Failure(StatsError.NETWORK)
        errorFor(status.code)?.let { return@withContext StatsResult.Failure(it) }

        val domains = http.get("$base/domains?status=blocked&from=-24h&limit=$TOP_LIMIT", headers)
        try {
            val (total, blocked) = NextDnsApiParser.parseStatus(status.body)
            // Daftar domain teratas opsional: kegagalannya tidak membatalkan ringkasan
            val top = domains?.takeIf { it.code == 200 }?.let { NextDnsApiParser.parseDomains(it.body) }.orEmpty()
            StatsResult.Success(NextDnsSummary(total, blocked, top))
        } catch (_: Exception) {
            StatsResult.Failure(StatsError.SERVER)
        }
    }

    private fun errorFor(code: Int): StatsError? = when (code) {
        200 -> null
        401, 403 -> StatsError.INVALID_KEY
        404 -> StatsError.PROFILE_NOT_FOUND
        else -> StatsError.SERVER
    }

    private companion object {
        const val API = "https://api.nextdns.io"
        const val TOP_LIMIT = 3
    }
}

object UrlConnectionHttpGet : HttpGet {
    override fun get(url: String, headers: Map<String, String>): HttpResponse? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * API key NextDNS milik pengguna (memberi akses penuh ke akun NextDNS-nya). Disimpan hanya di perangkat,
 * dikecualikan dari Auto Backup, dan hanya dikirim ke api.nextdns.io.
 */
class NextDnsStatsSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API, null))
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    fun setApiKey(key: String?) {
        val value = key?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit { if (value == null) remove(KEY_API) else putString(KEY_API, value) }
        _apiKey.value = value
    }

    companion object {
        const val PREFS = "nextdns_stats"
        private const val KEY_API = "api_key"
    }
}
