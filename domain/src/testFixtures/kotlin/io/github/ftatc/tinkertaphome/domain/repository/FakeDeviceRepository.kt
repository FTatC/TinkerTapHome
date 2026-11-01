package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.model.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Fake för tester. Sätt [devices] för att simulera ändringar i lagringen. */
class FakeDeviceRepository(initial: List<Device> = emptyList()) : DeviceRepository {

    val devices = MutableStateFlow(initial)

    override fun observeDevices(): Flow<List<Device>> = devices
}
