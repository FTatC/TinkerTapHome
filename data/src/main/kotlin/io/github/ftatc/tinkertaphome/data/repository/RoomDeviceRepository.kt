package io.github.ftatc.tinkertaphome.data.repository

import android.database.sqlite.SQLiteConstraintException
import io.github.ftatc.tinkertaphome.data.local.DeviceDao
import io.github.ftatc.tinkertaphome.data.local.DeviceEntity
import io.github.ftatc.tinkertaphome.data.local.toDomain
import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.map
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
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
            .map { entities -> entities.toDevicesOutcome() }
            .catch { cause -> emit(cause.toStorageFailureOrRethrow()) }

    override suspend fun save(address: BleAddress, name: String, token: AuthToken?): Outcome<Device, SaveDeviceError> =
        try {
            val id = dao.insert(DeviceEntity(address = address.value, name = name, authToken = token?.value))
            Outcome.Success(Device(id = id, address = address, name = name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteConstraintException) {
            // Det enda villkor som kan brytas vid insert är det unika indexet på adressen.
            Outcome.Failure(SaveDeviceError.AlreadySaved)
        } catch (e: Exception) {
            Outcome.Failure(SaveDeviceError.StorageUnavailable)
        }

    /** Ett token i databasen som inte längre är giltigt räknas som inget token, så att användaren får ange ett nytt. */
    override suspend fun authToken(address: BleAddress): Outcome<AuthToken?, DeviceError> =
        storage { dao.findByAddress(address.value)?.authToken?.let(AuthToken::parse) }

    override suspend fun updateAuthToken(address: BleAddress, token: AuthToken?): Outcome<Unit, DeviceError> =
        storage { dao.updateAuthToken(address.value, token?.value) }.map { }

    /** Kör ett anrop mot databasen och gör om fel till [DeviceError.StorageUnavailable]. Avbrott kastas vidare. */
    private suspend fun <T> storage(block: suspend () -> T): Outcome<T, DeviceError> =
        try {
            Outcome.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Failure(DeviceError.StorageUnavailable)
        }
}

/**
 * Returtypen är det bredare `Outcome`, inte `Outcome.Success`, så att `catch` i samma flöde kan
 * skicka ett `Outcome.Failure`.
 */
private fun List<DeviceEntity>.toDevicesOutcome(): Outcome<List<Device>, DeviceError> =
    Outcome.Success(mapNotNull { it.toDomain() })

/**
 * Översätter fel från Room till ett domänfel, så att UI:t kan visa ett feltillstånd i stället för
 * att appen kraschar. Alla [Exception] räknas som lagringsfel eftersom Room kastar flera olika typer,
 * till exempel `SQLiteException` och `IllegalStateException`. Avbrott och [Error] kastas vidare.
 */
private fun Throwable.toStorageFailureOrRethrow(): Outcome.Failure<DeviceError> = when (this) {
    is CancellationException -> throw this
    is Exception -> Outcome.Failure(DeviceError.StorageUnavailable)
    else -> throw this
}
