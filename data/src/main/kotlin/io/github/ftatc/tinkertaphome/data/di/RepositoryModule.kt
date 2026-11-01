package io.github.ftatc.tinkertaphome.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ftatc.tinkertaphome.data.repository.RoomDeviceRepository
import io.github.ftatc.tinkertaphome.domain.repository.DeviceRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    @Binds
    abstract fun bindDeviceRepository(impl: RoomDeviceRepository): DeviceRepository
}
