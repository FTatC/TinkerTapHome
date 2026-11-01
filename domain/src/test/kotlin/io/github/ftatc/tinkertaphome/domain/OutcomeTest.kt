package io.github.ftatc.tinkertaphome.domain

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
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

    @Test
    fun `takeUntilFailure passes on successes and stops right after the first failure`() = runTest {
        val outcomes = flowOf<Outcome<Int, String>>(
            Outcome.Success(1),
            Outcome.Success(2),
            Outcome.Failure("fel"),
            Outcome.Success(3),
        )

        assertEquals(
            listOf(Outcome.Success(1), Outcome.Success(2), Outcome.Failure("fel")),
            outcomes.takeUntilFailure().toList(),
        )
    }

    @Test
    fun `takeUntilFailure stops an endless flow after a failure`() = runTest {
        val endless = flow<Outcome<Int, String>> {
            emit(Outcome.Failure("fel"))
            awaitCancellation()
        }

        assertEquals(listOf(Outcome.Failure("fel")), endless.takeUntilFailure().toList())
    }
}
