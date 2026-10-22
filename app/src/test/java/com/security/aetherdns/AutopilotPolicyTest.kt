package com.security.aetherdns

import com.security.aetherdns.autopilot.AutopilotDecision
import com.security.aetherdns.autopilot.AutopilotHold
import com.security.aetherdns.autopilot.AutopilotHoldCodec
import com.security.aetherdns.autopilot.AutopilotInput
import com.security.aetherdns.autopilot.AutopilotPolicy
import com.security.aetherdns.autopilot.AutopilotReason
import com.security.aetherdns.data.ProtectionSnapshot
import com.security.aetherdns.util.Reachability
import com.security.aetherdns.util.Reachability.FAIL
import com.security.aetherdns.util.Reachability.OFFLINE
import com.security.aetherdns.util.Reachability.OK
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutopilotPolicyTest {

    private val ag = "dns.adguard-dns.com"
    private val cd = "p2.freedns.controld.com"
    private val mv = "adblock.dns.mullvad.net"
    private val ref = "1dot1dot1dot1.cloudflare-dns.com"
    private val alts = listOf(ag, cd, mv)

    private val on = ProtectionSnapshot(active = true, specifier = ag, paused = false)
    private val off = on.copy(active = false)

    private fun pausedHold(reason: AutopilotReason = AutopilotReason.DOT_BLOCKED) = AutopilotHold(ag, reason, off, 0)
    private fun switchedHold() = AutopilotHold(ag, AutopilotReason.RESOLVER_DOWN, on.copy(specifier = cd), 0, substitute = cd)

    private suspend fun decide(
        snapshot: ProtectionSnapshot = on,
        hold: AutopilotHold? = null,
        captive: Boolean = false,
        userPaused: Boolean = false,
        net: Map<String, Reachability>
    ): AutopilotDecision {
        val probed = mutableListOf<String>()
        return AutopilotPolicy.decide(AutopilotInput(snapshot, userPaused, hold, captive), alts, ref) {
            probed += it
            net[it] ?: OFFLINE
        }
    }

    @Test
    fun `healthy resolver means nothing to do`() = runTest {
        assertEquals(AutopilotDecision.None, decide(net = mapOf(ag to OK)))
    }

    @Test
    fun `captive portal pauses active protection`() = runTest {
        assertEquals(AutopilotDecision.Pause(AutopilotReason.CAPTIVE_PORTAL), decide(captive = true, net = emptyMap()))
        // Sudah dijeda Autopilot: tetap menunggu
        assertEquals(AutopilotDecision.None, decide(off, pausedHold(AutopilotReason.CAPTIVE_PORTAL), captive = true, net = emptyMap()))
    }

    @Test
    fun `encrypted dns blocked everywhere pauses`() = runTest {
        assertEquals(AutopilotDecision.Pause(AutopilotReason.DOT_BLOCKED), decide(net = mapOf(ag to FAIL, ref to FAIL)))
    }

    @Test
    fun `only the chosen resolver down switches to a sibling`() = runTest {
        assertEquals(AutopilotDecision.SwitchTo(mv), decide(net = mapOf(ag to FAIL, ref to OK, cd to FAIL, mv to OK)))
        assertEquals(
            AutopilotDecision.Pause(AutopilotReason.RESOLVER_DOWN),
            decide(net = mapOf(ag to FAIL, ref to OK, cd to FAIL, mv to FAIL))
        )
    }

    @Test
    fun `offline network never triggers changes`() = runTest {
        assertEquals(AutopilotDecision.None, decide(net = mapOf(ag to OFFLINE)))
        assertEquals(AutopilotDecision.None, decide(net = mapOf(ag to FAIL, ref to OFFLINE)))
    }

    @Test
    fun `restores the user's resolver when it works again`() = runTest {
        assertEquals(AutopilotDecision.Restore(ag), decide(off, pausedHold(), net = mapOf(ag to OK)))
        assertEquals(AutopilotDecision.Restore(ag), decide(on.copy(specifier = cd), switchedHold(), net = mapOf(ag to OK)))
    }

    @Test
    fun `healthy substitute is kept while the original is still down`() = runTest {
        assertEquals(
            AutopilotDecision.None,
            decide(on.copy(specifier = cd), switchedHold(), net = mapOf(ag to FAIL, ref to OK, cd to OK))
        )
    }

    @Test
    fun `manual choices always win`() = runTest {
        // Dimatikan pengguna (tanpa hold)
        assertEquals(AutopilotDecision.None, decide(off, net = mapOf(ag to FAIL, ref to FAIL)))
        // Jeda manual sedang berjalan
        assertEquals(AutopilotDecision.None, decide(userPaused = true, captive = true, net = emptyMap()))
        // Pengguna mengganti resolver setelah Autopilot memindahkan: hold tidak berlaku lagi
        val userChanged = on.copy(specifier = mv)
        assertNull(AutopilotPolicy.validHold(switchedHold(), userChanged))
        assertEquals(AutopilotDecision.None, decide(userChanged, switchedHold(), net = mapOf(mv to OK)))
    }

    @Test
    fun `hold codec round trips and rejects garbage`() {
        val hold = AutopilotHold(ag, AutopilotReason.RESOLVER_DOWN, on.copy(specifier = cd), 1234L, substitute = cd)
        assertEquals(hold, AutopilotHoldCodec.decode(AutopilotHoldCodec.encode(hold)))
        val pause = pausedHold(AutopilotReason.CAPTIVE_PORTAL)
        assertEquals(pause, AutopilotHoldCodec.decode(AutopilotHoldCodec.encode(pause)))
        assertNull(AutopilotHoldCodec.decode("rusak"))
        assertNull(AutopilotHoldCodec.decode(null))
    }
}
