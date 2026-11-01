package io.github.ftatc.tinkertaphome.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class OutcomeTest {

    @Test
    fun `map transforms the value of a success`() {
        val outcome: Outcome<Int, String> = Outcome.Success(2)

        assertEquals(Outcome.Success(4), outcome.map { it * 2 })
    }

    @Test
    fun `map leaves a failure unchanged and does not call transform`() {
        val outcome: Outcome<Int, String> = Outcome.Failure("fel")

        val mapped = outcome.map<Int, String, Int> { fail("transform ska inte anropas"); it }

        assertEquals(Outcome.Failure("fel"), mapped)
    }
}
