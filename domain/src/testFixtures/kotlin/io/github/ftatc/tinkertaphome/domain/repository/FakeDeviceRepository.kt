package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.model.DeviceError
import io.github.ftatc.tinkertaphome.domain.model.SaveDeviceError
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
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

    /** Sparade token per adress. */
    val tokens = mutableMapOf<BleAddress, AuthToken>()

    /** Om satt misslyckas [authToken] och [updateAuthToken] med det här felet. */
    var tokenError: DeviceError? = null

    override fun observeDevices(): Flow<Outcome<List<Device>, DeviceError>> =
        combine(devices, error) { devices, error ->
            if (error != null) Outcome.Failure(error) else Outcome.Success(devices)
        }

    override suspend fun save(address: BleAddress, name: String, token: AuthToken?): Outcome<Device, SaveDeviceError> {
        saveCalls += address to name
        saveError?.let { return Outcome.Failure(it) }
        if (devices.value.any { it.address == address }) return Outcome.Failure(SaveDeviceError.AlreadySaved)
        val device = Device(id = (devices.value.maxOfOrNull { it.id } ?: 0) + 1, address = address, name = name)
        devices.value += device
        token?.let { tokens[address] = it }
        return Outcome.Success(device)
    }

    override suspend fun authToken(address: BleAddress): Outcome<AuthToken?, DeviceError> =
        tokenError?.let { Outcome.Failure(it) } ?: Outcome.Success(tokens[address])

    override suspend fun updateAuthToken(address: BleAddress, token: AuthToken?): Outcome<Unit, DeviceError> {
        tokenError?.let { return Outcome.Failure(it) }
        if (devices.value.none { it.address == address }) return Outcome.Success(Unit)
        if (token == null) tokens.remove(address) else tokens[address] = token
        return Outcome.Success(Unit)
    }
}
