package io.github.ftatc.tinkertaphome.ui.permissions

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.ble.BlePermissionChecker
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class BlePermissionViewModel @Inject constructor(
    private val checker: BlePermissionChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<BlePermissionUiState> = _uiState.asStateFlow()

    /** Anropas när användaren har svarat på systemdialogen. */
    fun onPermissionResult(result: PermissionRequestResult) {
        _uiState.value = when (result) {
            PermissionRequestResult.Granted -> BlePermissionUiState.Granted
            PermissionRequestResult.Denied -> required(previouslyDenied = true)
            PermissionRequestResult.PermanentlyDenied -> BlePermissionUiState.PermanentlyDenied()
        }
    }

    /** Anropas när appens inställningar inte gick att öppna. */
    fun onSettingsUnavailable() {
        _uiState.update { state ->
            if (state is BlePermissionUiState.PermanentlyDenied) state.copy(settingsUnavailable = true) else state
        }
    }

    /**
     * Anropas när skärmen visas igen, till exempel efter att användaren har varit i appens
     * inställningar. Behörigheten kan ha ändrats utanför appen.
     */
    fun onResume() {
        if (checker.isGranted()) {
            _uiState.value = BlePermissionUiState.Granted
        } else if (_uiState.value == BlePermissionUiState.Granted) {
            _uiState.value = required(previouslyDenied = false)
        }
    }

    private fun initialState(): BlePermissionUiState =
        if (checker.isGranted()) BlePermissionUiState.Granted else required(previouslyDenied = false)

    private fun required(previouslyDenied: Boolean) =
        BlePermissionUiState.Required(checker.requiredPermissions, previouslyDenied)
}
