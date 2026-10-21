package com.security.aetherdns

import com.security.aetherdns.data.HttpGet
import com.security.aetherdns.data.HttpResponse
import com.security.aetherdns.data.NextDnsApiParser
import com.security.aetherdns.data.NextDnsClient
import com.security.aetherdns.data.NextDnsSummary
import com.security.aetherdns.data.StatsError
import com.security.aetherdns.data.StatsResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextDnsStatsTest {

    private val statusJson = """
        {"data":[{"status":"default","queries":9000},{"status":"blocked","queries":1200},{"status":"allowed","queries":300}],
         "meta":{"pagination":{"cursor":null}}}
    """.trimIndent()

    private val domainsJson = """
        {"data":[{"domain":"app-measurement.com","root":"app-measurement.com","queries":400},
                 {"domain":"googleads.g.doubleclick.net","root":"doubleclick.net","queries":250}]}
    """.trimIndent()

    @Test
    fun `status counts every query and blocked separately`() {
        assertEquals(10_500L to 1_200L, NextDnsApiParser.parseStatus(statusJson))
    }

    @Test
    fun `domains keep order and counts`() {
        assertEquals(
            listOf("app-measurement.com" to 400L, "googleads.g.doubleclick.net" to 250L),
            NextDnsApiParser.parseDomains(domainsJson)
        )
    }

    @Test
    fun `blocked percent rounds down and handles zero`() {
        assertEquals(11, NextDnsSummary(10_500, 1_200, emptyList()).blockedPercent)
        assertEquals(0, NextDnsSummary(0, 0, emptyList()).blockedPercent)
    }

    @Test
    fun `summary sends the api key and combines both endpoints`() = runTest {
        val seen = mutableListOf<Pair<String, String?>>()
        val client = NextDnsClient(HttpGet { url, headers ->
            seen += url to headers["X-Api-Key"]
            HttpResponse(200, if ("/status" in url) statusJson else domainsJson)
        })

        val result = client.summary("abc123", "secret")

        assertEquals(
            StatsResult.Success(NextDnsSummary(10_500, 1_200, NextDnsApiParser.parseDomains(domainsJson))),
            result
        )
        assertTrue(seen.all { (url, key) -> url.startsWith("https://api.nextdns.io/profiles/abc123/analytics/") && key == "secret" })
    }

    @Test
    fun `http errors map to typed failures`() = runTest {
        fun clientReturning(code: Int) = NextDnsClient(HttpGet { _, _ -> HttpResponse(code, """{"errors":[]}""") })

        assertEquals(StatsResult.Failure(StatsError.INVALID_KEY), clientReturning(403).summary("abc123", "k"))
        assertEquals(StatsResult.Failure(StatsError.PROFILE_NOT_FOUND), clientReturning(404).summary("abc123", "k"))
        assertEquals(StatsResult.Failure(StatsError.SERVER), clientReturning(500).summary("abc123", "k"))
        assertEquals(StatsResult.Failure(StatsError.NETWORK), NextDnsClient(HttpGet { _, _ -> null }).summary("abc123", "k"))
    }

    @Test
    fun `failing top domains still returns the summary`() = runTest {
        val client = NextDnsClient(HttpGet { url, _ ->
            if ("/status" in url) HttpResponse(200, statusJson) else HttpResponse(500, "")
        })
        assertEquals(StatsResult.Success(NextDnsSummary(10_500, 1_200, emptyList())), client.summary("abc123", "k"))
    }

    @Test
    fun `malformed json is a server error`() = runTest {
        val client = NextDnsClient(HttpGet { _, _ -> HttpResponse(200, "not json") })
        assertEquals(StatsResult.Failure(StatsError.SERVER), client.summary("abc123", "k"))
    }
}
