package io.github.ftatc.tinkertaphome.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.ConnectedDevice
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.usecase.AllOffUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveConnectedDevicesUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.ObserveOutputsUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.RetryConnectionUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.SetOutputUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.UpdateAuthTokenUseCase
import io.github.ftatc.tinkertaphome.ui.navigation.DeviceDetailRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel för detaljskärmen. Visar enhetens anslutningsstatus och nodens utgångar, och styr dem.
 *
 * Appen ansluter själv till sparade enheter medan den syns (se `ForegroundConnectionsViewModel`),
 * så skärmen har inga knappar för att ansluta eller koppla ner. Har ett fel stoppat anslutningen kan
 * användaren försöka igen, och har noden avvisat tokenet kan användaren ange ett nytt.
 *
 * Utgångarna visar alltid nodens State, inte det skickade värdet, eftersom noden kan begränsa värdet.
 * Ett kommando skickas i taget.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = DeviceDetailViewModel.Factory::class)
class DeviceDetailViewModel @AssistedInject constructor(
    @Assisted route: DeviceDetailRoute,
    observeConnectedDevices: ObserveConnectedDevicesUseCase,
    private val observeOutputs: ObserveOutputsUseCase,
    private val retryConnection: RetryConnectionUseCase,
    private val setOutput: SetOutputUseCase,
    private val allOff: AllOffUseCase,
    private val updateAuthToken: UpdateAuthTokenUseCase,
) : ViewModel() {

    private val input = MutableStateFlow(DetailInput())

    val uiState: StateFlow<DeviceDetailUiState> =
        observeConnectedDevices.device(route.deviceId)
            .flatMapLatest { it.withSession() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DeviceDetailUiState.Loading,
            )

    fun onRetryClick() {
        val device = content()?.device ?: return
        input.update { it.copy(commandError = null) }
        retryConnection(device.address)
    }

    /** Sätter [output] till [value]. Gör inget om ett annat kommando väntar på svar. */
    fun onOutputChange(output: Output, value: Int) {
        sendCommand { address -> setOutput(address, output, value) }
    }

    fun onAllOffClick() {
        sendCommand { address -> allOff(address) }
    }

    fun onTokenChange(token: String) {
        input.update { it.copy(token = token, tokenInvalid = false, tokenSaveFailed = false) }
    }

    /** Sparar det nya tokenet. Appen ansluter sedan igen och skickar `AUTH` med det. */
    fun onTokenSubmit() {
        val current = content() ?: return
        val token = AuthToken.parse(current.token)
        if (token == null) {
            input.update { it.copy(tokenInvalid = true) }
            return
        }
        viewModelScope.launch {
            when (updateAuthToken(current.device.address, token)) {
                is Outcome.Success -> input.update { DetailInput() }
                is Outcome.Failure -> input.update { it.copy(tokenSaveFailed = true) }
            }
        }
    }

    private fun sendCommand(command: suspend (BleAddress) -> Outcome<Unit, NodeFailure>) {
        val current = content() ?: return
        if (!current.canControl) return
        input.update { it.copy(isSending = true, commandError = null) }
        viewModelScope.launch {
            val result = command(current.device.address)
            input.update { it.copy(isSending = false, commandError = (result as? Outcome.Failure)?.error) }
        }
    }

    private fun content(): DeviceDetailUiState.Content? = uiState.value as? DeviceDetailUiState.Content

    private fun Outcome<ConnectedDevice?, DeviceError>.withSession(): Flow<DeviceDetailUiState> {
        val connected = (this as? Outcome.Success)?.value ?: return flowOf(toDeviceDetailUiState())
        return combine(observeOutputs(connected.device.address), input) { session, input ->
            DeviceDetailUiState.Content(
                device = connected.device,
                connection = connected.connection,
                session = session,
                isSending = input.isSending,
                commandError = input.commandError,
                token = input.token,
                tokenInvalid = input.tokenInvalid,
                tokenSaveFailed = input.tokenSaveFailed,
            )
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(route: DeviceDetailRoute): DeviceDetailViewModel
    }

    private companion object {
        /** Håller flödet vid liv över konfigurationsändringar, till exempel rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Det användaren har gjort på skärmen, som inte kommer från enheten. */
private data class DetailInput(
    val isSending: Boolean = false,
    val commandError: NodeFailure? = null,
    val token: String = "",
    val tokenInvalid: Boolean = false,
    val tokenSaveFailed: Boolean = false,
)

private fun Outcome<ConnectedDevice?, DeviceError>.toDeviceDetailUiState(): DeviceDetailUiState = when (this) {
    is Outcome.Failure -> DeviceDetailUiState.Error(error)
    is Outcome.Success -> value
        ?.let { DeviceDetailUiState.Content(it.device, it.connection) }
        ?: DeviceDetailUiState.NotFound
}
