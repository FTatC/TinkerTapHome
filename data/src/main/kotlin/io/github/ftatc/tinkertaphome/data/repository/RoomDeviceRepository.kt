package io.github.ftatc.tinkertaphome.data.repository

import io.github.ftatc.tinkertaphome.data.local.DeviceDao
import io.github.ftatc.tinkertaphome.data.local.toDomain
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal class RoomDeviceRepository @Inject constructor(
    private val dao: DeviceDao,
) : DeviceRepository {

    override fun observeDevices(): Flow<Outcome<List<Device>, DeviceError>> =
        dao.observeAll()
            .map<_, Outcome<List<Device>, DeviceError>> { entities ->
                Outcome.Success(entities.mapNotNull { it.toDomain() })
            }
            .catch { cause -> emit(cause.toStorageFailure()) }
}

/**
 * Översätter fel från Room till ett domänfel, så att UI:t kan visa ett feltillstånd i stället för
 * att appen kraschar. Alla [Exception] räknas som lagringsfel eftersom Room kastar flera olika typer,
 * till exempel `SQLiteException` och `IllegalStateException`. Avbrott och [Error] släpps igenom.
 */
private fun Throwable.toStorageFailure(): Outcome.Failure<DeviceError> = when (this) {
    is CancellationException -> throw this
    is Exception -> Outcome.Failure(DeviceError.StorageUnavailable)
    else -> throw this
}
