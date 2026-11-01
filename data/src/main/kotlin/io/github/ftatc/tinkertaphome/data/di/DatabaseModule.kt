package io.github.ftatc.tinkertaphome.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.ftatc.tinkertaphome.data.local.DeviceDao
import io.github.ftatc.tinkertaphome.data.local.TinkerTapDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TinkerTapDatabase =
        Room.databaseBuilder(context, TinkerTapDatabase::class.java, TinkerTapDatabase.NAME)
            .build()

    @Provides
    fun provideDeviceDao(database: TinkerTapDatabase): DeviceDao = database.deviceDao()
}
