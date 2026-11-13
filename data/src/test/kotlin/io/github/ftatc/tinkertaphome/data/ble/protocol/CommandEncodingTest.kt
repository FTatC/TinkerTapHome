package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.OutputId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exemplen kommer från tabellen "Exempel" i `docs/ble-protocol.md`. Testerna skyddar mot att
 * byteformatet ändras av misstag, eftersom riktiga noder förlitar sig på det.
 */
class CommandEncodingTest {

    @Test
    fun `encodes AUTH with the default token`() {
        assertEncodes("20 74 69 6E 6B 65 72", NodeCommand.Authenticate(AuthToken.DEFAULT))
    }

    @Test
    fun `encodes SET on and off for a digital output`() {
        assertEncodes("01 00 01", NodeCommand.SetDigital(OutputId(0), on = true))
        assertEncodes("01 00 00", NodeCommand.SetDigital(OutputId(0), on = false))
    }

    @Test
    fun `encodes TOGGLE`() {
        assertEncodes("02 00", NodeCommand.Toggle(OutputId(0)))
    }

    @Test
    fun `encodes PWM at 50 percent`() {
        assertEncodes("03 01 80", NodeCommand.SetPwm(OutputId(1), value = 128))
    }

    @Test
    fun `encodes PWM at full value as an unsigned byte`() {
        assertEncodes("03 01 FF", NodeCommand.SetPwm(OutputId(1), value = 255))
    }

    @Test
    fun `encodes SERVO at 90 degrees`() {
        assertEncodes("04 02 5A", NodeCommand.SetServo(OutputId(2), angle = 90))
    }

    @Test
    fun `encodes commands without an output as a single opcode byte`() {
        assertEncodes("11", NodeCommand.Ping)
        assertEncodes("10", NodeCommand.GetState)
        assertEncodes("FF", NodeCommand.AllOff)
    }

    @Test
    fun `keeps every command within the 20 byte limit`() {
        val longest = NodeCommand.Authenticate(AuthToken.parse("x".repeat(AuthToken.MAX_LENGTH))!!)

        assertEquals(20, longest.encode().size)
    }

    @Test
    fun `reports the output a command targets`() {
        assertEquals(OutputId(3), NodeCommand.Toggle(OutputId(3)).targetOutput)
        assertNull(NodeCommand.AllOff.targetOutput)
    }

    @Test
    fun `uses the protocol UUIDs`() {
        assertEquals(UUID.fromString("7d3c0000-5b2e-4a8f-9c61-2e4b8a1f7c90"), NodeProtocol.SERVICE)
        assertEquals(UUID.fromString("7d3c0001-5b2e-4a8f-9c61-2e4b8a1f7c90"), NodeProtocol.COMMAND.characteristic)
        assertEquals(UUID.fromString("7d3c0002-5b2e-4a8f-9c61-2e4b8a1f7c90"), NodeProtocol.STATE.characteristic)
        assertEquals(UUID.fromString("7d3c0003-5b2e-4a8f-9c61-2e4b8a1f7c90"), NodeProtocol.INFO.characteristic)
        assertEquals(UUID.fromString("7d3c0004-5b2e-4a8f-9c61-2e4b8a1f7c90"), NodeProtocol.RESULT.characteristic)
    }

    private fun assertEncodes(expectedHex: String, command: NodeCommand) {
        assertEquals(expectedHex, command.encode().toHex())
    }
}

internal fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

internal fun hex(value: String): ByteArray =
    value.split(" ").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
