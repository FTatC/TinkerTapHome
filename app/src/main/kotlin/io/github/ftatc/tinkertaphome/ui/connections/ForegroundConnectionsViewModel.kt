package io.github.ftatc.tinkertaphome.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ftatc.tinkertaphome.domain.usecase.KeepSavedDevicesConnectedUseCase
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Håller sparade enheter anslutna medan appen syns och kopplar ner dem när den går till bakgrunden
 * (fråga 5 i `docs/open-questions.md`). Anslutningar i bakgrunden hör till fas 7.
 *
 * Nedkopplingen väntar [BACKGROUND_GRACE], så att en rotation av skärmen, som också stoppar
 * aktiviteten en kort stund, inte kopplar ner alla enheter.
 */
@HiltViewModel
class ForegroundConnectionsViewModel @Inject constructor(
    private val keepSavedDevicesConnected: KeepSavedDevicesConnectedUseCase,
) : ViewModel() {

    private var keeping: Job? = null
    private var stopping: Job? = null

    fun onForeground() {
        stopping?.cancel()
        stopping = null
        if (keeping?.isActive == true) return
        keeping = viewModelScope.launch { keepSavedDevicesConnected() }
    }

    fun onBackground() {
        if (stopping?.isActive == true) return
        stopping = viewModelScope.launch {
            delay(BACKGROUND_GRACE)
            keeping?.cancel()
            keeping = null
        }
    }

    companion object {
        val BACKGROUND_GRACE: Duration = 5.seconds
    }
}
