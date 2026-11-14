package io.github.ftatc.tinkertaphome.domain.usecase

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.FakeNodeSessions
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAuthTokenUseCaseTest {

    private val lampa = testDevice(id = 1, name = "Lampa")
    private val repository = FakeDeviceRepository(listOf(lampa))
    private val connection = FakeDeviceConnection()
    private val sessions = FakeNodeSessions(connection)
    private val useCase = UpdateAuthTokenUseCase(repository, RetryConnectionUseCase(connection, sessions))

    private val token = checkNotNull(AuthToken.parse("nytt"))

    @Test
    fun `saves the token and connects again`() = runTest {
        assertEquals(Outcome.Success(Unit), useCase(lampa.address, token))

        assertEquals(token, repository.tokens[lampa.address])
        assertEquals(listOf(lampa.address), connection.disconnectCalls)
        assertEquals(listOf(lampa.address), sessions.resetCalls)
    }

    @Test
    fun `does not connect again when the token cannot be saved`() = runTest {
        repository.tokenError = DeviceError.StorageUnavailable

        assertEquals(Outcome.Failure(DeviceError.StorageUnavailable), useCase(lampa.address, token))
        assertTrue(connection.disconnectCalls.isEmpty())
        assertTrue(sessions.resetCalls.isEmpty())
    }
}
