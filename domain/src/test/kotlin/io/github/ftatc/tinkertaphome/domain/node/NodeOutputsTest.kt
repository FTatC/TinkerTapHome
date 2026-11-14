package io.github.ftatc.tinkertaphome.domain.node

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeOutputsTest {

    private val led = Output(OutputId(0), "led", OutputType.Digital)
    private val fan = Output(OutputId(1), "fan", OutputType.Pwm)
    private val info = testNodeInfo(outputs = listOf(led, fan))

    @Test
    fun `pairs every output with its value`() {
        assertEquals(listOf(OutputValue(led, 1), OutputValue(fan, 128)), NodeOutputs(info, listOf(1, 128)).outputs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `refuses values that do not match the outputs`() {
        NodeOutputs(info, listOf(1))
    }
}
