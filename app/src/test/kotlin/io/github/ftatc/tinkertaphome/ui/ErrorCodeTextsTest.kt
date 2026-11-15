package io.github.ftatc.tinkertaphome.ui

import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.ui.device.connectFailedMessage
import io.github.ftatc.tinkertaphome.ui.scan.scanFailedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorCodeTextsTest {

    @Test
    fun `scanning too often asks the user to wait`() {
        assertEquals(R.string.scan_error_too_frequent, scanFailedMessage(6))
    }

    @Test
    fun `a phone without BLE scanning is explained`() {
        assertEquals(R.string.scan_error_unsupported, scanFailedMessage(4))
    }

    @Test
    fun `errors in the Bluetooth stack ask the user to restart Bluetooth`() {
        listOf(2, 3, 5).forEach { assertEquals(R.string.scan_error_restart_bluetooth, scanFailedMessage(it)) }
    }

    @Test
    fun `other scan codes use the general text`() {
        assertNull(scanFailedMessage(1))
        assertNull(scanFailedMessage(99))
    }

    @Test
    fun `connection statuses have texts the user can act on`() {
        assertEquals(R.string.connection_error_out_of_reach, connectFailedMessage(8))
        assertEquals(R.string.connection_error_closed_by_device, connectFailedMessage(19))
        assertEquals(R.string.connection_error_closed_by_phone, connectFailedMessage(22))
        assertEquals(R.string.connection_error_establish_failed, connectFailedMessage(62))
        assertEquals(R.string.connection_error_gatt_error, connectFailedMessage(133))
    }

    @Test
    fun `other connection statuses use the general text`() {
        assertNull(connectFailedMessage(42))
    }
}
