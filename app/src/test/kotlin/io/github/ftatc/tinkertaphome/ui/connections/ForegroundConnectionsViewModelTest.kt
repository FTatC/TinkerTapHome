package io.github.ftatc.tinkertaphome.ui.connections

import io.github.ftatc.tinkertaphome.domain.ble.FakeDeviceConnection
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState
import io.github.ftatc.tinkertaphome.domain.model.testDevice
import io.github.ftatc.tinkertaphome.domain.node.FakeNodeSessions
import io.github.ftatc.tinkertaphome.domain.repository.FakeDeviceRepository
import io.github.ftatc.tinkertaphome.domain.usecase.KeepSavedDevicesConnectedUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveDevicesUseCase
import io.github.ftatc.tinkertaphome.ui.MainDispatcherRule
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ForegroundConnectionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val lampa = testDevice(id = 1, name = "Lampa")
    private val connection = FakeDeviceConnection()
    private val repository = FakeDeviceRepository(listOf(lampa))
    private val viewModel = ForegroundConnectionsViewModel(
        KeepSavedDevicesConnectedUseCase(ObserveDevicesUseCase(repository), repository, connection, FakeNodeSessions(connection)),
    )

    private fun state() = connection.observeState(lampa.address).value

    @Test
    fun `connects saved devices when the app is shown`() = runTest {
        viewModel.onForeground()
        runCurrent()

        assertEquals(ConnectionState.Connected, state())
    }

    @Test
    fun `disconnects shortly after the app goes to the background`() = runTest {
        viewModel.onForeground()
        runCurrent()

        viewModel.onBackground()
        runCurrent()
        assertEquals(ConnectionState.Connected, state())

        advanceTimeBy(ForegroundConnectionsViewModel.BACKGROUND_GRACE + 1.seconds)
        assertEquals(ConnectionState.Disconnected, state())
    }

    @Test
    fun `stays connected when the app is shown again within the grace period`() = runTest {
        // Till exempel när skärmen roteras, vilket stoppar och startar aktiviteten.
        viewModel.onForeground()
        runCurrent()

        viewModel.onBackground()
        viewModel.onForeground()
        advanceTimeBy(ForegroundConnectionsViewModel.BACKGROUND_GRACE * 2)

        assertEquals(ConnectionState.Connected, state())
        assertEquals(1, connection.connectCalls.size)
        assertEquals(emptyList<Any>(), connection.disconnectCalls)
    }

    @Test
    fun `connects again when the app is shown after being in the background`() = runTest {
        viewModel.onForeground()
        runCurrent()
        viewModel.onBackground()
        advanceTimeBy(ForegroundConnectionsViewModel.BACKGROUND_GRACE + 1.seconds)

        viewModel.onForeground()
        runCurrent()

        assertEquals(ConnectionState.Connected, state())
        assertEquals(2, connection.connectCalls.size)
    }
}
