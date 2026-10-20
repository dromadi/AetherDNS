package com.security.aetherdns

import com.security.aetherdns.ui.BenchmarkState
import com.security.aetherdns.util.DotProbeCause
import com.security.aetherdns.util.DotProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BenchmarkStateTest {

    @Test
    fun fastestIgnoresFailures() {
        val state = BenchmarkState(
            results = mapOf(
                "a" to DotProbeResult.Success(handshakeMs = 80, queryMs = 20),
                "b" to DotProbeResult.Failure(DotProbeCause.TIMEOUT, "timeout"),
                "c" to DotProbeResult.Success(handshakeMs = 30, queryMs = 15)
            )
        )
        assertEquals("c", state.fastestId)
    }

    @Test
    fun noSuccessfulProbeMeansNoFastest() {
        val state = BenchmarkState(results = mapOf("a" to DotProbeResult.Failure(DotProbeCause.TLS, "tls")))
        assertNull(state.fastestId)
    }
}
