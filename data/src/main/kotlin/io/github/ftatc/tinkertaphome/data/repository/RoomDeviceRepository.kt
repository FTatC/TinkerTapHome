package io.github.ftatc.tinkertaphome.data.repository

import io.github.ftatc.tinkertaphome.data.local.DeviceDao
import io.github.ftatc.tinkertaphome.data.local.toDomain
import io.github.ftatc.tinkertaphome.domain.model.Device
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomDeviceRepository @Inject constructor(
    private val dao: DeviceDao,
) : DeviceRepository {

    override fun observeDevices(): Flow<List<Device>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }
}
