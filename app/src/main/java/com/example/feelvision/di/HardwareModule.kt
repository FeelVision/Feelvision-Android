package com.feelvision.di

import com.feelvision.hardware.HardwareSource
import com.feelvision.hardware.PhoneCameraSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
abstract class HardwareModule {
    // Phase 2: swap to LuckfoxSource here — nothing else changes
    @Binds @Singleton
    abstract fun bindHardwareSource(impl: PhoneCameraSource): HardwareSource
}