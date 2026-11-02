package com.security.aetherdns

import com.security.aetherdns.automation.AutomationCommand
import com.security.aetherdns.automation.AutomationProtocol
import com.security.aetherdns.automation.AutomationProtocol.Result.Accepted
import com.security.aetherdns.automation.AutomationProtocol.Result.Rejected
import com.security.aetherdns.automation.AutomationRejection
import com.security.aetherdns.model.DnsProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationProtocolTest {

    private val token = "0123456789abcdef0123456789abcdef"

    private val profiles = listOf(
        DnsProfile(id = "adguard_default", hostname = "dns.adguard-dns.com", name = "AdGuard DNS"),
        DnsProfile(id = "saved_1", hostname = "dns.home.example", name = "Rumah – Pi-hole", isCustom = true)
    )

    private fun parse(action: String?, vararg extras: Pair<String, String?>, expected: String = token) =
        AutomationProtocol.parse(action, mapOf(*extras), expected, profiles) { it.name }

    private fun withToken(action: String, vararg extras: Pair<String, String?>) =
        parse(action, AutomationProtocol.EXTRA_TOKEN to token, *extras)

    @Test
    fun missingOrWrongTokenIsRejectedBeforeAnythingElse() {
        assertEquals(Rejected(AutomationRejection.BadToken), parse(AutomationProtocol.ACTION_DISABLE))
        assertEquals(Rejected(AutomationRejection.BadToken), parse(AutomationProtocol.ACTION_DISABLE, AutomationProtocol.EXTRA_TOKEN to "wrong"))
        assertEquals(Rejected(AutomationRejection.BadToken), parse("bogus.action", AutomationProtocol.EXTRA_TOKEN to ""))
    }

    @Test
    fun emptyStoredTokenRejectsEverything() {
        // Token kosong (otomasi belum pernah diaktifkan) tidak boleh cocok dengan token kosong dari intent
        assertEquals(Rejected(AutomationRejection.BadToken), parse(AutomationProtocol.ACTION_DISABLE, AutomationProtocol.EXTRA_TOKEN to "", expected = ""))
    }

    @Test
    fun tokenToleratesSurroundingWhitespaceOnly() {
        assertTrue(AutomationProtocol.tokenMatches("  $token\n", token))
        assertFalse(AutomationProtocol.tokenMatches(token.uppercase(), token))
        assertFalse(AutomationProtocol.tokenMatches(token.dropLast(1), token))
    }

    @Test
    fun enableAndDisable() {
        assertEquals(Accepted(AutomationCommand.Enable), withToken(AutomationProtocol.ACTION_ENABLE))
        assertEquals(Accepted(AutomationCommand.Disable), withToken(AutomationProtocol.ACTION_DISABLE))
    }

    @Test
    fun pauseDefaultsTo15MinutesAndValidatesRange() {
        assertEquals(Accepted(AutomationCommand.Pause(15)), withToken(AutomationProtocol.ACTION_PAUSE))
        assertEquals(Accepted(AutomationCommand.Pause(30)), withToken(AutomationProtocol.ACTION_PAUSE, AutomationProtocol.EXTRA_MINUTES to " 30 "))
        assertEquals(
            Rejected(AutomationRejection.InvalidMinutes("0")),
            withToken(AutomationProtocol.ACTION_PAUSE, AutomationProtocol.EXTRA_MINUTES to "0")
        )
        assertEquals(
            Rejected(AutomationRejection.InvalidMinutes("abc")),
            withToken(AutomationProtocol.ACTION_PAUSE, AutomationProtocol.EXTRA_MINUTES to "abc")
        )
        assertEquals(
            Rejected(AutomationRejection.InvalidMinutes("1441")),
            withToken(AutomationProtocol.ACTION_PAUSE, AutomationProtocol.EXTRA_MINUTES to "1441")
        )
    }

    @Test
    fun applyByHostnameTakesPrecedenceOverProfile() {
        assertEquals(
            Accepted(AutomationCommand.Apply("dns.example.net")),
            withToken(
                AutomationProtocol.ACTION_APPLY,
                AutomationProtocol.EXTRA_HOSTNAME to " dns.example.net ",
                AutomationProtocol.EXTRA_PROFILE to "AdGuard DNS"
            )
        )
    }

    @Test
    fun applyByProfileMatchesIdOrNameIgnoringCase() {
        assertEquals(
            Accepted(AutomationCommand.Apply("dns.home.example")),
            withToken(AutomationProtocol.ACTION_APPLY, AutomationProtocol.EXTRA_PROFILE to "rumah – pi-hole")
        )
        assertEquals(
            Accepted(AutomationCommand.Apply("dns.adguard-dns.com")),
            withToken(AutomationProtocol.ACTION_APPLY, AutomationProtocol.EXTRA_PROFILE to "ADGUARD_DEFAULT")
        )
    }

    @Test
    fun applyWithoutTargetOrUnknownProfileIsRejected() {
        assertEquals(Rejected(AutomationRejection.MissingTarget), withToken(AutomationProtocol.ACTION_APPLY))
        assertEquals(
            Rejected(AutomationRejection.UnknownProfile("Kantor")),
            withToken(AutomationProtocol.ACTION_APPLY, AutomationProtocol.EXTRA_PROFILE to "Kantor")
        )
    }

    @Test
    fun unknownActionIsRejected() {
        assertEquals(Rejected(AutomationRejection.UnknownAction), withToken("com.security.aetherdns.action.REBOOT"))
        assertEquals(Rejected(AutomationRejection.UnknownAction), parse(null, AutomationProtocol.EXTRA_TOKEN to token))
    }
}
