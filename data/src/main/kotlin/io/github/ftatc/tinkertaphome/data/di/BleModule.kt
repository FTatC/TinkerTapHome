package io.github.ftatc.tinkertaphome.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ftatc.tinkertaphome.data.ble.AndroidBleScanSource
import io.github.ftatc.tinkertaphome.data.ble.AndroidDeviceScanner
import io.github.ftatc.tinkertaphome.data.ble.BleScanSource
import io.github.ftatc.tinkertaphome.data.ble.gatt.AndroidDeviceConnection
import io.github.ftatc.tinkertaphome.data.ble.gatt.AndroidGattConnector
import io.github.ftatc.tinkertaphome.data.ble.gatt.GattConnector
import io.github.ftatc.tinkertaphome.data.ble.protocol.GattNodeClient
import io.github.ftatc.tinkertaphome.domain.ble.DeviceConnection
import io.github.ftatc.tinkertaphome.domain.ble.DeviceScanner
import io.github.ftatc.tinkertaphome.domain.node.NodeClient

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BleModule {

    @Binds
    abstract fun bindDeviceScanner(impl: AndroidDeviceScanner): DeviceScanner

    @Binds
    abstract fun bindBleScanSource(impl: AndroidBleScanSource): BleScanSource

    @Binds
    abstract fun bindDeviceConnection(impl: AndroidDeviceConnection): DeviceConnection

    @Binds
    abstract fun bindGattConnector(impl: AndroidGattConnector): GattConnector

    @Binds
    abstract fun bindNodeClient(impl: GattNodeClient): NodeClient
}
