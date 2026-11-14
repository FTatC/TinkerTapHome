package io.github.ftatc.tinkertaphome.data.ble

import io.github.ftatc.tinkertaphome.data.ble.protocol.NodeProtocol
import io.github.ftatc.tinkertaphome.domain.model.DeviceNames
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `removes hidden characters from an advertised name`() {
        // Regressionstest: namnet kommer från vem som helst i närheten och visades och föreslogs
        // som det var, inklusive tecken som vänder textriktningen.
        val event = RawScanEvent.Result(address = testAddress(1).value, name = "La\u202Empa\u0000", rssi = -55)

        assertEquals("Lampa", event.toScannedDevice()?.name)
    }

    @Test
    fun `shortens a long advertised name to the maximum length`() {
        val event = RawScanEvent.Result(address = testAddress(1).value, name = "a".repeat(500), rssi = -55)

        assertEquals("a".repeat(DeviceNames.MAX_LENGTH), event.toScannedDevice()?.name)
    }

    @Test
    fun `a name of only hidden characters is treated as no name`() {
        val event = RawScanEvent.Result(address = testAddress(1).value, name = "\u200B\u202E", rssi = -55)

        assertNull(event.toScannedDevice()?.name)
    }

    @Test
    fun `marks a device that advertises the TinkerTapNode service as a node`() {
        val node = RawScanEvent.Result(address = testAddress(1).value, name = "TinkerTapNode-A1B2", rssi = -55, serviceUuids = listOf(NodeProtocol.SERVICE))
        val other = RawScanEvent.Result(address = testAddress(2).value, name = "Klocka", rssi = -55, serviceUuids = listOf(UUID.randomUUID()))

        assertTrue(node.toScannedDevice()!!.isTinkerTapNode)
        assertFalse(other.toScannedDevice()!!.isTinkerTapNode)
    }
}
