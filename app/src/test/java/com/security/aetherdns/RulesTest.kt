package com.security.aetherdns

import com.security.aetherdns.rules.NetworkType
import com.security.aetherdns.rules.RuleDecision
import com.security.aetherdns.rules.RuleEngine
import com.security.aetherdns.rules.RuleSet
import com.security.aetherdns.rules.RuleSource
import com.security.aetherdns.rules.RuleTarget
import com.security.aetherdns.rules.RulesCodec
import com.security.aetherdns.rules.ScheduleRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class RulesTest {

    // Senin 22 Okt 2029 sengaja tidak dipakai; 2026-10-19 = Senin
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    private val night = ScheduleRule("n", "Malam", setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), 21 * 60, 6 * 60, "family.adguard-dns.com")
    private val office = ScheduleRule("o", "Kantor", setOf(DayOfWeek.MONDAY), 9 * 60, 17 * 60, "dns.quad9.net")

    @Test
    fun `daytime schedule matches only inside its window and days`() {
        assertTrue(office.isActive(at(19, 9)))
        assertTrue(office.isActive(at(19, 16, 59)))
        assertFalse(office.isActive(at(19, 17)))
        assertFalse(office.isActive(at(20, 10))) // Selasa
    }

    @Test
    fun `overnight schedule continues past midnight into the next day`() {
        assertTrue(night.isActive(at(19, 22)))          // Senin malam
        assertTrue(night.isActive(at(20, 5, 59)))       // Selasa dini hari (lanjutan Senin)
        assertFalse(night.isActive(at(20, 6)))
        assertTrue(night.isActive(at(21, 3)))           // Rabu dini hari (lanjutan Selasa)
        assertFalse(night.isActive(at(22, 3)))          // Kamis dini hari: Rabu tidak terjadwal
        assertFalse(night.isActive(at(19, 12)))
    }

    @Test
    fun `schedules beat network rules, which beat the main choice`() {
        val rules = RuleSet(enabled = true, wifi = RuleTarget.Fastest, cellular = RuleTarget.Resolver("p2.freedns.controld.com"), schedules = listOf(night))
        assertEquals(RuleDecision(RuleTarget.Resolver("family.adguard-dns.com"), RuleSource.Schedule(night)), RuleEngine.decide(rules, NetworkType.WIFI, at(19, 23)))
        assertEquals(RuleDecision(RuleTarget.Fastest, RuleSource.Network(NetworkType.WIFI)), RuleEngine.decide(rules, NetworkType.WIFI, at(19, 12)))
        assertEquals(
            RuleDecision(RuleTarget.Resolver("p2.freedns.controld.com"), RuleSource.Network(NetworkType.CELLULAR)),
            RuleEngine.decide(rules, NetworkType.CELLULAR, at(19, 12))
        )
        assertNull(RuleEngine.decide(rules, NetworkType.OTHER, at(19, 12)))
        assertNull(RuleEngine.decide(rules.copy(enabled = false), NetworkType.WIFI, at(19, 23)))
    }

    @Test
    fun `next boundary is the closest start or end`() {
        val rules = RuleSet(enabled = true, schedules = listOf(night, office))
        assertEquals(at(19, 9), RuleEngine.nextBoundary(rules, at(19, 8, 30)))
        assertEquals(at(19, 17), RuleEngine.nextBoundary(rules, at(19, 9)))
        assertEquals(at(19, 21), RuleEngine.nextBoundary(rules, at(19, 17, 1)))
        assertEquals(at(20, 6), RuleEngine.nextBoundary(rules, at(19, 21, 30)))
        assertNull(RuleEngine.nextBoundary(RuleSet(enabled = true), at(19, 9)))
    }

    @Test
    fun `codec round trips and survives garbage`() {
        val rules = RuleSet(enabled = true, wifi = RuleTarget.Fastest, cellular = RuleTarget.Resolver("dns.quad9.net"), schedules = listOf(night, office))
        assertEquals(rules, RulesCodec.decode(RulesCodec.encode(rules)))
        assertEquals(RuleSet(), RulesCodec.decode("bukan json"))
        assertEquals(RuleSet(), RulesCodec.decode(null))
    }
}
