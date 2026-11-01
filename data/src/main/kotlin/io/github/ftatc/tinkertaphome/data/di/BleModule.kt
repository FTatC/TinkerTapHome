package io.github.ftatc.tinkertaphome.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ftatc.tinkertaphome.data.ble.AndroidBleScanSource
import io.github.ftatc.tinkertaphome.data.ble.AndroidDeviceScanner
import io.github.ftatc.tinkertaphome.data.ble.BleScanSource
import io.github.ftatc.tinkertaphome.domain.repository.DeviceScanner

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BleModule {

    @Binds
    abstract fun bindDeviceScanner(impl: AndroidDeviceScanner): DeviceScanner

    @Binds
    abstract fun bindBleScanSource(impl: AndroidBleScanSource): BleScanSource
}
