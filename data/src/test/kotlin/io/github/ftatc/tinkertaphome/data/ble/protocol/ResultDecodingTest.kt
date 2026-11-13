package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.NodeCommand
import io.github.ftatc.tinkertaphome.domain.node.OutputId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exemplen kommer från tabellerna "Exempel" och "Result" i `docs/ble-protocol.md`. */
class ResultDecodingTest {

    @Test
    fun `decodes a successful answer for an output`() {
        assertEquals(NodeResult(opcode = 0x01, deviceId = 0, status = 0x00), NodeResult.decode(hex("01 00 00")))
    }

    @Test
    fun `decodes device id 0xFF as no output`() {
        assertEquals(NodeResult(opcode = 0x20, deviceId = null, status = 0x00), NodeResult.decode(hex("20 FF 00")))
    }

    @Test
    fun `ignores values that are not exactly three bytes`() {
        assertNull(NodeResult.decode(hex("01 00")))
        assertNull(NodeResult.decode(hex("01 00 00 00")))
    }

    @Test
    fun `maps every status code to its outcome`() {
        assertEquals(Outcome.Success(Unit), outcomeOf("01 00 00"))
        assertEquals(Outcome.Failure(CommandError.UnknownCommand), outcomeOf("42 FF 01"))
        assertEquals(Outcome.Failure(CommandError.InvalidOutput), outcomeOf("01 09 02"))
        assertEquals(Outcome.Failure(CommandError.WrongType), outcomeOf("03 00 03"))
        assertEquals(Outcome.Failure(CommandError.MissingValue), outcomeOf("01 00 04"))
        assertEquals(Outcome.Failure(CommandError.NotAuthorized), outcomeOf("01 FF 05"))
        assertEquals(Outcome.Failure(CommandError.AuthFailed), outcomeOf("20 FF 06"))
        assertEquals(Outcome.Failure(CommandError.BadLength), outcomeOf("00 FF 07"))
    }

    @Test
    fun `never treats an unknown status as success`() {
        assertEquals(Outcome.Failure(CommandError.Unknown(0x80)), outcomeOf("01 00 80"))
    }

    @Test
    fun `answers a command with the same opcode and output`() {
        assertTrue(decode("01 00 00").answers(NodeCommand.SetDigital(OutputId(0), on = true)))
    }

    @Test
    fun `answers a command on an output when the node names no output`() {
        // NOT_AUTHORIZED anger alltid 0xFF, även när kommandot gällde en utgång.
        assertTrue(decode("01 FF 05").answers(NodeCommand.SetDigital(OutputId(0), on = true)))
    }

    @Test
    fun `answers commands without an output`() {
        assertTrue(decode("20 FF 00").answers(NodeCommand.Authenticate(AuthToken.DEFAULT)))
        assertTrue(decode("FF FF 00").answers(NodeCommand.AllOff))
    }

    @Test
    fun `does not answer a command with another opcode`() {
        assertFalse(decode("02 00 00").answers(NodeCommand.SetDigital(OutputId(0), on = true)))
    }

    @Test
    fun `does not answer a command on another output`() {
        assertFalse(decode("01 01 00").answers(NodeCommand.SetDigital(OutputId(0), on = true)))
    }

    private fun decode(value: String): NodeResult = requireNotNull(NodeResult.decode(hex(value)))

    private fun outcomeOf(value: String) = decode(value).toOutcome()
}
