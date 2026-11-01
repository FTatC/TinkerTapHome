package io.github.ftatc.tinkertaphome.data.ble

import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanResultMapperTest {

    @Test
    fun `maps address, name and signal strength`() {
        val event = RawScanEvent.Result(address = "aa:bb:cc:dd:ee:01", name = "Lampa", rssi = -55)

        assertEquals(ScannedDevice(address = testAddress(1), name = "Lampa", rssi = -55), event.toScannedDevice())
    }

    @Test
    fun `returns null for an invalid address`() {
        assertNull(RawScanEvent.Result(address = "inte en adress", name = "Lampa", rssi = -55).toScannedDevice())
    }

    @Test
    fun `treats a blank name as no name`() {
        assertNull(RawScanEvent.Result(address = testAddress(1).value, name = "  ", rssi = -55).toScannedDevice()?.name)
    }

    @Test
    fun `trims whitespace around the name`() {
        assertEquals("Lampa", RawScanEvent.Result(address = testAddress(1).value, name = " Lampa ", rssi = -55).toScannedDevice()?.name)
    }
}
