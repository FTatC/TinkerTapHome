package io.github.ftatc.tinkertaphome.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ftatc.tinkertaphome.data.permission.AndroidBlePermissionChecker
import io.github.ftatc.tinkertaphome.data.permission.ContextPermissionStatus
import io.github.ftatc.tinkertaphome.data.permission.PermissionStatus
import io.github.ftatc.tinkertaphome.domain.ble.BlePermissionChecker

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PermissionModule {

    @Binds
    abstract fun bindBlePermissionChecker(impl: AndroidBlePermissionChecker): BlePermissionChecker

    @Binds
    abstract fun bindPermissionStatus(impl: ContextPermissionStatus): PermissionStatus
}
