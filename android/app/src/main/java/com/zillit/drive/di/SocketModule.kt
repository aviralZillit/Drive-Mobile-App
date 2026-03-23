package com.zillit.drive.di

import com.zillit.drive.data.remote.socket.DriveSocketManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SocketModule {

    @Provides
    @Singleton
    fun provideDriveSocketManager(): DriveSocketManager = DriveSocketManager()
}
