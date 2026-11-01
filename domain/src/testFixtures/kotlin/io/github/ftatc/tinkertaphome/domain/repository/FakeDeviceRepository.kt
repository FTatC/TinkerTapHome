package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Fake för tester. Sätt [devices] för att simulera ändringar i lagringen,
 * och [error] för att simulera att lagringen inte går att läsa.
 */
class FakeDeviceRepository(initial: List<Device> = emptyList()) : DeviceRepository {

    val devices = MutableStateFlow(initial)

    val error = MutableStateFlow<DeviceError?>(null)

    override fun observeDevices(): Flow<Outcome<List<Device>, DeviceError>> =
        combine(devices, error) { devices, error ->
            if (error != null) Outcome.Failure(error) else Outcome.Success(devices)
        }
}
