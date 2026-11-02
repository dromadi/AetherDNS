package com.security.aetherdns

import com.security.aetherdns.ui.BenchmarkState
import com.security.aetherdns.util.DotProbeCause
import com.security.aetherdns.util.DotProbeResult
import com.security.aetherdns.util.FilterReport
import com.security.aetherdns.util.InspectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BenchmarkStateTest {

    private fun success(handshakeMs: Long, queryMs: Long) = InspectionResult.Success(
        DotProbeResult.Success(handshakeMs, queryMs),
        FilterReport(blocked = listOf("ads.example"), allowed = emptyList(), unknown = emptyList())
    )

    @Test
    fun fastestIgnoresFailures() {
        val state = BenchmarkState(
            results = mapOf(
                "a" to success(handshakeMs = 80, queryMs = 20),
                "b" to InspectionResult.Failure(DotProbeResult.Failure(DotProbeCause.TIMEOUT)),
                "c" to success(handshakeMs = 30, queryMs = 15)
            )
        )
        assertEquals("c", state.fastestId)
    }

    @Test
    fun noSuccessfulInspectionMeansNoFastest() {
        val state = BenchmarkState(results = mapOf("a" to InspectionResult.Failure(DotProbeResult.Failure(DotProbeCause.TLS))))
        assertNull(state.fastestId)
    }
}
