package com.feelvision.di

import com.feelvision.hardware.LuckfoxBridge
import com.feelvision.luckfox.LuckfoxTcpServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LuckfoxModule {

    @Provides
    @Singleton
    fun provideLuckfoxBridge(): LuckfoxBridge {
        return LuckfoxTcpServer()
    }
}
