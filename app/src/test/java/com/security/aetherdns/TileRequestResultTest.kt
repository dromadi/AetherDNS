package com.security.aetherdns

import com.security.aetherdns.ui.TileRequestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regresi: tombol "Pasang di Quick Settings" dulu diam saja apa pun jawaban sistem. */
class TileRequestResultTest {
    @Test
    fun `every system answer except a user decline produces a message`() {
        assertEquals(R.string.toast_tile_added, TileRequestResult.messageFor(2))
        assertEquals(R.string.toast_tile_already_added, TileRequestResult.messageFor(1))
        assertNull(TileRequestResult.messageFor(0))
        listOf(1000, 1001, 1002, 1003, 1004, 1005).forEach {
            assertEquals(R.string.toast_tile_manual, TileRequestResult.messageFor(it))
        }
    }
}
