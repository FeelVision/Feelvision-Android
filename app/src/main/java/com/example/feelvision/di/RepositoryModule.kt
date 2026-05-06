package com.feelvision.di

import com.feelvision.data.people.PeopleRepository
import com.feelvision.data.people.PeopleRepositoryImpl
import com.feelvision.data.settings.SettingsRepository
import com.feelvision.data.settings.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindPeople  (impl: PeopleRepositoryImpl)  : PeopleRepository
    @Binds @Singleton abstract fun bindSettings(impl: SettingsRepositoryImpl): SettingsRepository
}