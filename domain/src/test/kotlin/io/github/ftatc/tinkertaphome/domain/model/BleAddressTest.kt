package io.github.ftatc.tinkertaphome.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleAddressTest {

    @Test
    fun `parses a valid upper case address unchanged`() {
        assertEquals("AA:BB:CC:DD:EE:FF", BleAddress.parse("AA:BB:CC:DD:EE:FF")?.value)
    }

    @Test
    fun `normalizes lower case to upper case`() {
        assertEquals("0A:1B:2C:3D:4E:5F", BleAddress.parse("0a:1b:2c:3d:4e:5f")?.value)
    }

    @Test
    fun `addresses differing only in case are equal`() {
        // Regressionstest: tidigare var adressen en String, och "aa:…" och "AA:…" räknades som två enheter.
        assertEquals(BleAddress.parse("aa:bb:cc:dd:ee:ff"), BleAddress.parse("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `ignores surrounding whitespace`() {
        assertEquals("AA:BB:CC:DD:EE:FF", BleAddress.parse("  AA:BB:CC:DD:EE:FF\n")?.value)
    }

    @Test
    fun `rejects too few octets`() {
        assertNull(BleAddress.parse("AA:BB:CC:DD:EE"))
    }

    @Test
    fun `rejects too many octets`() {
        assertNull(BleAddress.parse("AA:BB:CC:DD:EE:FF:00"))
    }

    @Test
    fun `rejects other separators`() {
        assertNull(BleAddress.parse("AA-BB-CC-DD-EE-FF"))
        assertNull(BleAddress.parse("AABBCCDDEEFF"))
    }

    @Test
    fun `rejects non hexadecimal characters`() {
        assertNull(BleAddress.parse("GG:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `rejects octets with wrong length`() {
        assertNull(BleAddress.parse("A:BB:CC:DD:EE:FFF"))
    }

    @Test
    fun `rejects empty string`() {
        assertNull(BleAddress.parse(""))
    }

    @Test
    fun `toString returns the normalized address`() {
        assertEquals("AA:BB:CC:DD:EE:FF", BleAddress.parse("aa:bb:cc:dd:ee:ff").toString())
    }
}
