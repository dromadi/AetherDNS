package com.security.aetherdns

import com.security.aetherdns.shizuku.GrantError
import com.security.aetherdns.shizuku.GrantException
import com.security.aetherdns.shizuku.GrantOutputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantOutputParserTest {

    @Test
    fun actualPermissionStateWinsOverExitCode() {
        assertTrue(GrantOutputParser.toResult("1\nsome warning", permissionGranted = true).isSuccess)
    }

    @Test
    fun zeroExitWithoutPermissionIsFailure() {
        assertTrue(GrantOutputParser.toResult("0\n", permissionGranted = false).isFailure)
    }

    private fun errorOf(raw: String) =
        (GrantOutputParser.toResult(raw, permissionGranted = false).exceptionOrNull() as GrantException).error

    @Test
    fun oemSecurityExceptionIsRecognised() {
        val raw = "255\nException occurred while executing 'grant':\njava.lang.SecurityException: grantRuntimePermission: Neither user 2000 nor current process has android.permission.GRANT_RUNTIME_PERMISSIONS."
        assertEquals(GrantError.OemRestricted, errorOf(raw))
    }

    @Test
    fun genericFailureKeepsExitCodeAndFirstOutputLine() {
        val raw = "255\nUnknown package: com.security.aetherdns\nmore details"
        assertEquals(GrantError.PmFailed("255", "Unknown package: com.security.aetherdns"), errorOf(raw))
    }

    @Test
    fun failureWithoutOutputHasNoOutputLine() {
        assertEquals(GrantError.PmFailed("1", null), errorOf("1\n"))
    }
}
