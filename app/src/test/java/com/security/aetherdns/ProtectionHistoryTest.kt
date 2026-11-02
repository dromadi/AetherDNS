package com.security.aetherdns

import com.security.aetherdns.data.ChangeSource
import com.security.aetherdns.data.FileHistoryStore
import com.security.aetherdns.data.HistoryAction
import com.security.aetherdns.data.HistoryCodec
import com.security.aetherdns.data.HistoryEvent
import com.security.aetherdns.data.HistoryTracker
import com.security.aetherdns.data.ProtectionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProtectionHistoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val off = ProtectionSnapshot(active = false, specifier = "dns.adguard-dns.com", paused = false)
    private val on = off.copy(active = true)
    private val paused = off.copy(paused = true)

    private fun action(prev: ProtectionSnapshot?, next: ProtectionSnapshot) =
        HistoryTracker.transition(prev, next, ChangeSource.APP, 1L)?.action

    @Test
    fun `transitions map to actions`() {
        assertEquals(HistoryAction.ENABLED, action(off, on))
        assertEquals(HistoryAction.DISABLED, action(on, off))
        assertEquals(HistoryAction.PAUSED, action(on, paused))
        assertEquals(HistoryAction.RESUMED, action(paused, on))
        assertEquals(HistoryAction.RESOLVER_CHANGED, action(on, on.copy(specifier = "family.adguard-dns.com")))
    }

    @Test
    fun `first observation and non-events record nothing`() {
        assertNull(action(null, on))
        assertNull(action(on, on))
        assertNull(action(on, on.copy(specifier = "DNS.ADGUARD-DNS.COM")))
        // Specifier diganti saat proteksi mati tidak mengubah proteksi
        assertNull(action(off, off.copy(specifier = "one.one.one.one")))
        // Jeda dibatalkan tanpa mengaktifkan kembali
        assertNull(action(paused, off))
    }

    @Test
    fun `event keeps source and resolver`() {
        val event = HistoryTracker.transition(off, on, ChangeSource.TILE, 42L)
        assertEquals(HistoryEvent(42L, HistoryAction.ENABLED, ChangeSource.TILE, "dns.adguard-dns.com"), event)
    }

    @Test
    fun `codec round trips and skips garbage`() {
        val event = HistoryEvent(5L, HistoryAction.PAUSED, ChangeSource.AUTOMATION, "x.dns.nextdns.io")
        assertEquals(event, HistoryCodec.decode(HistoryCodec.encode(event)))
        assertEquals(on, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(on)))
        assertNull(HistoryCodec.decode("E\tnope"))
        assertNull(HistoryCodec.decode("S\ttrue\tfalse\tx"))
    }

    @Test
    fun `counts only within window`() {
        val events = listOf(
            HistoryEvent(10L, HistoryAction.PAUSED, ChangeSource.APP, ""),
            HistoryEvent(90L, HistoryAction.PAUSED, ChangeSource.TILE, ""),
            HistoryEvent(95L, HistoryAction.DISABLED, ChangeSource.SYSTEM, "")
        )
        assertEquals(mapOf(HistoryAction.PAUSED to 1, HistoryAction.DISABLED to 1), HistoryTracker.counts(events, 100L, 20L))
    }

    @Test
    fun `file store prunes old events and clear keeps snapshot`() {
        var now = 0L
        val store = FileHistoryStore(tmp.root) { now }
        store.saveSnapshot(on)
        store.append(HistoryEvent(0L, HistoryAction.ENABLED, ChangeSource.APP, "a"))
        now = FileHistoryStore.MAX_AGE_MS + 10
        store.append(HistoryEvent(now, HistoryAction.DISABLED, ChangeSource.APP, "a"))
        assertEquals(listOf(HistoryAction.DISABLED), store.events().map { it.action })
        store.clear()
        assertEquals(emptyList<HistoryEvent>(), store.events())
        assertEquals(on, FileHistoryStore(tmp.root).lastSnapshot())
    }
}
