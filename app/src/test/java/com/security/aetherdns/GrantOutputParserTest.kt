package com.security.aetherdns

import com.security.aetherdns.shizuku.GrantOutputParser
import org.junit.Assert.assertFalse
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

    @Test
    fun oemSecurityExceptionShowsDeveloperOptionHint() {
        val raw = "255\nException occurred while executing 'grant':\njava.lang.SecurityException: grantRuntimePermission: Neither user 2000 nor current process has android.permission.GRANT_RUNTIME_PERMISSIONS."
        val message = GrantOutputParser.toResult(raw, permissionGranted = false).exceptionOrNull()!!.message!!
        assertTrue(message.contains("MIUI/HyperOS"))
    }

    @Test
    fun genericFailureShowsFirstOutputLineWithoutOemHint() {
        val raw = "255\nUnknown package: com.security.aetherdns\nmore details"
        val message = GrantOutputParser.toResult(raw, permissionGranted = false).exceptionOrNull()!!.message!!
        assertTrue(message.contains("Unknown package: com.security.aetherdns"))
        assertFalse(message.contains("more details"))
        assertFalse(message.contains("MIUI"))
    }
}
