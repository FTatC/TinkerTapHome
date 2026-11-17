package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testAddress
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.node.OutputId
import io.github.ftatc.tinkertaphome.domain.node.OutputType
import io.github.ftatc.tinkertaphome.domain.repository.FakeOutputPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushButtonUseCasesTest {

    private val address = testAddress(1)
    private val repository = FakeOutputPreferencesRepository()
    private val observe = ObservePushButtonsUseCase(repository)
    private val set = SetPushButtonUseCase(repository)

    private val button = Output(OutputId(1), "btn", OutputType.Digital)

    @Test
    fun `no output is a push button until the user chooses it`() = runTest {
        assertEquals(Outcome.Success(emptySet<String>()), observe(address).first())
    }

    @Test
    fun `saves the choice by output name`() = runTest {
        set(address, button, pushButton = true)

        assertEquals(Outcome.Success(setOf("btn")), observe(address).first())
    }

    @Test
    fun `turns a push button back into a switch`() = runTest {
        set(address, button, pushButton = true)

        set(address, button, pushButton = false)

        assertEquals(Outcome.Success(emptySet<String>()), observe(address).first())
    }

    @Test
    fun `keeps the choices for each node apart`() = runTest {
        set(address, button, pushButton = true)

        assertEquals(Outcome.Success(emptySet<String>()), observe(testAddress(2)).first())
    }

    @Test
    fun `reports a storage error`() = runTest {
        repository.writeError = DeviceError.StorageUnavailable

        assertEquals(Outcome.Failure(DeviceError.StorageUnavailable), set(address, button, pushButton = true))
    }

    @Test
    fun `only a digital output can be a push button`() = runTest {
        val fan = Output(OutputId(2), "fan", OutputType.Pwm)

        val thrown = runCatching { set(address, fan, pushButton = true) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        assertEquals(emptyMap<Any, Any>(), repository.pushButtons.value)
    }
}
