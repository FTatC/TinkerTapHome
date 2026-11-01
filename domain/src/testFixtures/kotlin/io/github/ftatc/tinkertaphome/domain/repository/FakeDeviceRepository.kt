package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Fake för tester. Sätt [devices] för att simulera ändringar i lagringen,
 * [error] för att simulera att lagringen inte går att läsa och [saveError] för att få [save] att misslyckas.
 */
class FakeDeviceRepository(initial: List<Device> = emptyList()) : DeviceRepository {

    val devices = MutableStateFlow(initial)

    val error = MutableStateFlow<DeviceError?>(null)

    var saveError: SaveDeviceError? = null

    /** Alla anrop till [save], i ordning. */
    val saveCalls = mutableListOf<Pair<BleAddress, String>>()

    override fun observeDevices(): Flow<Outcome<List<Device>, DeviceError>> =
        combine(devices, error) { devices, error ->
            if (error != null) Outcome.Failure(error) else Outcome.Success(devices)
        }

    override suspend fun save(address: BleAddress, name: String): Outcome<Device, SaveDeviceError> {
        saveCalls += address to name
        saveError?.let { return Outcome.Failure(it) }
        if (devices.value.any { it.address == address }) return Outcome.Failure(SaveDeviceError.AlreadySaved)
        val device = Device(id = (devices.value.maxOfOrNull { it.id } ?: 0) + 1, address = address, name = name)
        devices.value += device
        return Outcome.Success(device)
    }
}
