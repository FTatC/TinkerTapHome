package io.github.ftatc.tinkertaphome.ui.save

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.usecase.SaveDeviceUseCase
import io.github.ftatc.tinkertaphome.ui.navigation.SaveDeviceRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel för dialogen som sparar en hittad enhet. Rutten skickas in av Hilt som assisted-argument,
 * så att adressen och det föreslagna namnet finns från start.
 */
@HiltViewModel(assistedFactory = SaveDeviceViewModel.Factory::class)
class SaveDeviceViewModel @AssistedInject constructor(
    @Assisted route: SaveDeviceRoute,
    private val saveDevice: SaveDeviceUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SaveDeviceUiState(
            // Rutten skapas bara från en adress som redan är validerad, så den är alltid giltig.
            address = checkNotNull(BleAddress.parse(route.address)) { "Ogiltig adress i rutten: ${route.address}" },
            name = route.suggestedName.orEmpty(),
        ),
    )
    val uiState: StateFlow<SaveDeviceUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, saveError = null) }
    }

    /** Sparar enheten. Vid fel ligger dialogen kvar och visar felet. */
    fun onSaveConfirm() {
        val current = _uiState.value
        if (current.isSaving || current.isSaved) return
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            val result = saveDevice(current.address, current.name)
            _uiState.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(isSaving = false, isSaved = true)
                    is Outcome.Failure -> state.copy(isSaving = false, saveError = result.error)
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(route: SaveDeviceRoute): SaveDeviceViewModel
    }
}
