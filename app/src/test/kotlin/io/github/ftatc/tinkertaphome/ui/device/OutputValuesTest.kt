package io.github.ftatc.tinkertaphome.ui.device

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputValuesTest {

    @Test
    fun `shows the ends of the pwm range as 0 and 100 percent`() {
        assertEquals(0, pwmPercent(0))
        assertEquals(100, pwmPercent(255))
    }

    @Test
    fun `rounds pwm values to the nearest percent`() {
        assertEquals(50, pwmPercent(128))
        assertEquals(1, pwmPercent(2))
    }
}
