package io.github.ftatc.tinkertaphome.domain.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NodeCommandTest {

    @Test
    fun `accepts the whole PWM and servo ranges`() {
        NodeCommand.SetPwm(OutputId(0), value = 0)
        NodeCommand.SetPwm(OutputId(0), value = 255)
        NodeCommand.SetServo(OutputId(0), angle = 0)
        NodeCommand.SetServo(OutputId(0), angle = 180)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a PWM value above 255`() {
        NodeCommand.SetPwm(OutputId(0), value = 256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a negative PWM value`() {
        NodeCommand.SetPwm(OutputId(0), value = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a servo angle above 180`() {
        NodeCommand.SetServo(OutputId(0), angle = 181)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects the output id reserved for no output`() {
        OutputId(0xFF)
    }

    @Test
    fun `accepts a token of 1 to 19 visible ASCII characters`() {
        assertEquals("t", AuthToken.parse("t")?.value)
        assertEquals("a b~!", AuthToken.parse("a b~!")?.value)
        assertEquals(19, AuthToken.parse("x".repeat(19))?.value?.length)
    }

    @Test
    fun `rejects an empty or too long token`() {
        assertNull(AuthToken.parse(""))
        assertNull(AuthToken.parse("x".repeat(20)))
    }

    @Test
    fun `rejects a token with characters outside visible ASCII`() {
        assertNull(AuthToken.parse("tinkér"))
        assertNull(AuthToken.parse("tink\ner"))
    }

    @Test
    fun `keeps spaces in a token because it must match exactly`() {
        assertEquals(" tinker ", AuthToken.parse(" tinker ")?.value)
    }

    @Test
    fun `never shows the token in text`() {
        assertFalse(AuthToken.DEFAULT.toString().contains("tinker"))
    }
}
