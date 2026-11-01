package io.github.ftatc.tinkertaphome.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.ScannedDevice
import io.github.ftatc.tinkertaphome.domain.usecase.SaveDeviceUseCase
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
    private val saveDevice: SaveDeviceUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    /**
     * Startar en ny skanning. En pågående skanning ersätts och tidigare resultat rensas.
     * En öppen namndialog ligger kvar, eftersom skanningen startas om varje gång skärmen blir synlig.
     */
    fun startScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(devices = emptyList(), isScanning = true, error = null) }
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

    /** Öppnar namndialogen för [device], med enhetens annonserade namn som förslag. */
    fun onDeviceClick(device: ScannedDevice) {
        _uiState.update { it.copy(saveDialog = SaveDialogState(device = device, name = device.name.orEmpty())) }
    }

    fun onNameChange(name: String) {
        _uiState.update { state -> state.copy(saveDialog = state.saveDialog?.copy(name = name, error = null)) }
    }

    fun onSaveDismiss() {
        _uiState.update { it.copy(saveDialog = null) }
    }

    /** Sparar enheten i dialogen. Vid fel ligger dialogen kvar och visar felet. */
    fun onSaveConfirm() {
        val dialog = _uiState.value.saveDialog ?: return
        if (dialog.isSaving) return
        _uiState.update { it.copy(saveDialog = dialog.copy(isSaving = true, error = null)) }
        viewModelScope.launch {
            when (val result = saveDevice(dialog.device.address, dialog.name)) {
                is Outcome.Success -> {
                    stopScan()
                    _uiState.update { it.copy(saveDialog = null, savedDevice = result.value) }
                }
                is Outcome.Failure -> _uiState.update { state ->
                    state.copy(saveDialog = state.saveDialog?.copy(isSaving = false, error = result.error))
                }
            }
        }
    }
}
