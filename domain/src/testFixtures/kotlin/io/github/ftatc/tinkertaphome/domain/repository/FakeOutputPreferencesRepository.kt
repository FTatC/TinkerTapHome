package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Fake för tester. Sätt [pushButtons] direkt, [readError] för att läsningen ska misslyckas eller
 * [writeError] för att [setPushButton] ska misslyckas.
 */
class FakeOutputPreferencesRepository : OutputPreferencesRepository {

    /** Utgångarnas namn per nod. */
    val pushButtons = MutableStateFlow<Map<BleAddress, Set<String>>>(emptyMap())

    var readError: DeviceError? = null

    var writeError: DeviceError? = null

    override fun observePushButtons(address: BleAddress): Flow<Outcome<Set<String>, DeviceError>> =
        pushButtons.map { all ->
            readError?.let { Outcome.Failure(it) } ?: Outcome.Success(all[address].orEmpty())
        }

    override suspend fun setPushButton(
        address: BleAddress,
        outputName: String,
        pushButton: Boolean,
    ): Outcome<Unit, DeviceError> {
        writeError?.let { return Outcome.Failure(it) }
        pushButtons.update { all ->
            val names = all[address].orEmpty()
            all + (address to if (pushButton) names + outputName else names - outputName)
        }
        return Outcome.Success(Unit)
    }
}
