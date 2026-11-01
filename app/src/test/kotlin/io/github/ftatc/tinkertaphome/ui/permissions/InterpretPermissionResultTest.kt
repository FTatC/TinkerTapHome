package io.github.ftatc.tinkertaphome.ui.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class InterpretPermissionResultTest {

    private val scan = "android.permission.BLUETOOTH_SCAN"
    private val connect = "android.permission.BLUETOOTH_CONNECT"

    @Test
    fun `granted when every permission is granted`() {
        val result = interpretPermissionResult(mapOf(scan to true, connect to true)) { false }

        assertEquals(PermissionRequestResult.Granted, result)
    }

    @Test
    fun `denied when a denied permission can be asked for again`() {
        val result = interpretPermissionResult(mapOf(scan to false, connect to true)) { it == scan }

        assertEquals(PermissionRequestResult.Denied, result)
    }

    @Test
    fun `permanently denied when no denied permission can be asked for again`() {
        val result = interpretPermissionResult(mapOf(scan to false, connect to false)) { false }

        assertEquals(PermissionRequestResult.PermanentlyDenied, result)
    }

    @Test
    fun `denied when one denied permission can still be asked for`() {
        val result = interpretPermissionResult(mapOf(scan to false, connect to false)) { it == connect }

        assertEquals(PermissionRequestResult.Denied, result)
    }

    @Test
    fun `dismissing the dialog without answering counts as denied and not granted`() {
        // Regressionstest: en tom karta får inte tolkas som att allt är beviljat.
        val result = interpretPermissionResult(emptyMap()) { false }

        assertEquals(PermissionRequestResult.Denied, result)
    }
}
