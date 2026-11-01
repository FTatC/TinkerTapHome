package io.github.ftatc.tinkertaphome.data.permission

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidBlePermissionCheckerTest {

    private val granted = mutableSetOf<String>()
    private val checker = AndroidBlePermissionChecker { permission -> permission in granted }

    @Test
    fun `requires scan and connect`() {
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
            checker.requiredPermissions,
        )
    }

    @Test
    fun `not granted when no permission is granted`() {
        assertFalse(checker.isGranted())
    }

    @Test
    fun `not granted when only scan is granted`() {
        granted += Manifest.permission.BLUETOOTH_SCAN

        assertFalse(checker.isGranted())
    }

    @Test
    fun `not granted when only connect is granted`() {
        granted += Manifest.permission.BLUETOOTH_CONNECT

        assertFalse(checker.isGranted())
    }

    @Test
    fun `granted when all required permissions are granted`() {
        granted += listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

        assertTrue(checker.isGranted())
    }
}
