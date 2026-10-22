package com.security.aetherdns.adb

import com.security.aetherdns.shizuku.GrantError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class ActivationModelTest {

    @Test
    fun `pairing code accepts six digits and ignores separators`() {
        assertEquals("542332", PairingCode.normalize("542332"))
        assertEquals("542332", PairingCode.normalize(" 542 332 "))
        assertEquals("542332", PairingCode.normalize("542-332"))
    }

    @Test
    fun `pairing code rejects wrong length or non digits`() {
        assertNull(PairingCode.normalize(""))
        assertNull(PairingCode.normalize("54233"))
        assertNull(PairingCode.normalize("5423321"))
        assertNull(PairingCode.normalize("54a332"))
        assertNull(PairingCode.normalize("٥٤٢٣٣٢")) // digit non-ASCII tidak diterima adbd
    }

    @Test
    fun `socket failures mean the pairing service is unreachable`() {
        assertEquals(ActivationError.PairingUnreachable, ActivationFailures.pairing(ConnectException("refused")))
        assertEquals(ActivationError.PairingUnreachable, ActivationFailures.pairing(SocketTimeoutException()))
        assertTrue(ActivationFailures.isUnreachable(ConnectException()))
    }

    @Test
    fun `failed message exchange after connecting means the code was rejected`() {
        val error = ActivationFailures.pairing(IOException("Exchanging message wasn't successful."))
        assertEquals(ActivationError.PairingRejected, error)
        assertFalse(ActivationFailures.isUnreachable(IOException()))
    }

    @Test
    fun `tls failures are not reported as a wrong code`() {
        assertTrue(ActivationFailures.pairing(SSLHandshakeException("x")) is ActivationError.Unexpected)
        assertTrue(ActivationFailures.pairing(IllegalStateException("x")) is ActivationError.Unexpected)
    }

    @Test
    fun `granted permission wins over pm output`() {
        assertNull(ActivationFailures.grant("Exception occurred", permissionGranted = true))
    }

    @Test
    fun `oem restriction is detected from pm output`() {
        val error = ActivationFailures.grant(
            "java.lang.SecurityException: grantRuntimePermission: Neither user 2000 nor current process has " +
                "android.permission.GRANT_RUNTIME_PERMISSIONS.",
            permissionGranted = false
        )
        assertEquals(ActivationError.Grant(GrantError.OemRestricted), error)
    }

    @Test
    fun `other pm failures keep the first output line`() {
        val error = ActivationFailures.grant("Unknown package: x\nmore", permissionGranted = false)
        assertEquals(ActivationError.Grant(GrantError.PmFailed("?", "Unknown package: x")), error)
    }

    @Test
    fun `empty pm output without permission is still a failure`() {
        assertEquals(ActivationError.Grant(GrantError.PmFailed("?", null)), ActivationFailures.grant("  ", permissionGranted = false))
    }
}
