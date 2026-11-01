package io.github.ftatc.tinkertaphome.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.usecase.ScanForDevicesUseCase
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ViewModel för skanningsskärmen. Sköter bara skanningen. Att spara en enhet görs i en egen dialog. */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanForDevices: ScanForDevicesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    /** Startar en ny skanning. En pågående skanning ersätts och tidigare resultat rensas. */
    fun startScan() {
        scanJob?.cancel()
        _uiState.value = ScanUiState(isScanning = true)
        scanJob = viewModelScope.launch {
            scanForDevices().collect { outcome -> _uiState.update { it.withScanResult(outcome) } }
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
