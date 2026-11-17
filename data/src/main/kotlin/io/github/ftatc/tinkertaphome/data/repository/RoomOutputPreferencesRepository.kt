package io.github.ftatc.tinkertaphome.data.repository

import io.github.ftatc.tinkertaphome.data.local.PushButtonDao
import io.github.ftatc.tinkertaphome.data.local.PushButtonEntity
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.repository.OutputPreferencesRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal class RoomOutputPreferencesRepository @Inject constructor(
    private val dao: PushButtonDao,
) : OutputPreferencesRepository {

    override fun observePushButtons(address: BleAddress): Flow<Outcome<Set<String>, DeviceError>> =
        dao.observeOutputNames(address.value)
            .map<List<String>, Outcome<Set<String>, DeviceError>> { names -> Outcome.Success(names.toSet()) }
            .catch { cause -> emit(cause.toStorageFailureOrRethrow()) }

    override suspend fun setPushButton(
        address: BleAddress,
        outputName: String,
        pushButton: Boolean,
    ): Outcome<Unit, DeviceError> =
        try {
            if (pushButton) {
                dao.insert(PushButtonEntity(deviceAddress = address.value, outputName = outputName))
            } else {
                dao.delete(address.value, outputName)
            }
            Outcome.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Även en nod som inte är sparad hamnar här, eftersom den främmande nyckeln bryts.
            Outcome.Failure(DeviceError.StorageUnavailable)
        }
}
