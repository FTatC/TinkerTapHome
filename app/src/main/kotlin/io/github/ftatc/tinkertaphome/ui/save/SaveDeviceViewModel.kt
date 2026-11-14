package io.github.ftatc.tinkertaphome.ui.save

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.usecase.PairNodeUseCase
import io.github.ftatc.tinkertaphome.domain.usecase.SaveNodeUseCase
import io.github.ftatc.tinkertaphome.ui.navigation.SaveDeviceRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel för dialogen som sparar en TinkerTapNode: knappen på noden, parkoppling, namn och token,
 * och sedan AUTH och sparande. Stängs dialogen innan noden är sparad kopplas noden ner.
 */
@HiltViewModel(assistedFactory = SaveDeviceViewModel.Factory::class)
class SaveDeviceViewModel @AssistedInject constructor(
    @Assisted route: SaveDeviceRoute,
    private val pairNode: PairNodeUseCase,
    private val saveNode: SaveNodeUseCase,
) : ViewModel() {

    // Rutten skapas bara från en adress som redan är validerad, så den är alltid giltig.
    private val address: BleAddress =
        checkNotNull(BleAddress.parse(route.address)) { "Ogiltig adress i rutten: ${route.address}" }

    private val _uiState = MutableStateFlow(SaveDeviceUiState(address = address, name = route.suggestedName.orEmpty()))
    val uiState: StateFlow<SaveDeviceUiState> = _uiState.asStateFlow()

    /** Användaren har tryckt på nodens knapp. Parkopplar och läser nodens Info. */
    fun onPairClick() {
        if (_uiState.value.step != SaveStep.PressButton) return
        _uiState.update { it.copy(step = SaveStep.Pairing, pairError = null) }
        viewModelScope.launch {
            val result = pairNode(address)
            _uiState.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(step = SaveStep.Details, info = result.value)
                    is Outcome.Failure -> state.copy(step = SaveStep.PressButton, pairError = result.error)
                }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, saveError = null) }
    }

    fun onTokenChange(token: String) {
        _uiState.update { it.copy(token = token, tokenInvalid = false, saveError = null) }
    }

    /** Skickar AUTH om noden kräver det och sparar noden. Vid fel ligger dialogen kvar och visar felet. */
    fun onSaveConfirm() {
        val current = _uiState.value
        val info = current.info ?: return
        if (current.step != SaveStep.Details) return
        val token = if (current.showsTokenField) AuthToken.parse(current.token) else null
        if (current.showsTokenField && token == null) {
            _uiState.update { it.copy(tokenInvalid = true) }
            return
        }
        _uiState.update { it.copy(step = SaveStep.Saving, saveError = null) }
        viewModelScope.launch {
            val result = saveNode(address, current.name, info, token)
            _uiState.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(step = SaveStep.Saved)
                    is Outcome.Failure -> state.copy(step = SaveStep.Details, saveError = result.error)
                }
            }
        }
    }

    override fun onCleared() {
        if (!_uiState.value.isSaved) pairNode.abort(address)
    }

    @AssistedFactory
    interface Factory {
        fun create(route: SaveDeviceRoute): SaveDeviceViewModel
    }
}
