package io.github.ftatc.tinkertaphome.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.usecase.ScanForDevicesUseCase
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanForDevices: ScanForDevicesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    /** Startar en ny skanning. En pågående skanning ersätts. Hittade enheter rensas. */
    fun startScan() {
        scanJob?.cancel()
        _uiState.value = ScanUiState(isScanning = true)
        scanJob = viewModelScope.launch {
            scanForDevices().collect { outcome ->
                when (outcome) {
                    is Outcome.Success -> _uiState.update { it.copy(devices = outcome.value) }
                    is Outcome.Failure -> _uiState.update { it.copy(error = outcome.error) }
                }
            }
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    /** Stoppar skanningen, till exempel när skärmen inte längre syns. Hittade enheter ligger kvar. */
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }
}
