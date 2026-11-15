package io.github.ftatc.tinkertaphome.data.ble.gatt

import android.bluetooth.BluetoothStatusCodes
import org.junit.Assert.assertEquals
import org.junit.Test

class GattStatusCodesTest {

    @Test
    fun `success starts the operation`() {
        assertEquals(GattRequest.STARTED, BluetoothStatusCodes.SUCCESS.statusToRequest())
    }

    @Test
    fun `a missing permission is reported as such and not as a busy stack`() {
        assertEquals(
            GattRequest.PERMISSION_DENIED,
            BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION.statusToRequest(),
        )
    }

    @Test
    fun `bluetooth that is off or not running is reported as not connected`() {
        assertEquals(GattRequest.NOT_CONNECTED, BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED.statusToRequest())
        assertEquals(GattRequest.NOT_CONNECTED, BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND.statusToRequest())
    }

    @Test
    fun `other codes mean that android refused`() {
        assertEquals(GattRequest.REJECTED, BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY.statusToRequest())
    }
}
