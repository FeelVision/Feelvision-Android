package com.feelvision.di

import android.content.Context
import androidx.room.Room
import com.feelvision.data.db.AppDatabase
import com.feelvision.data.people.PeopleDao
import com.feelvision.data.people.PeopleRepository
import com.feelvision.data.people.PeopleRepositoryImpl
import com.feelvision.data.settings.SettingsRepository
import com.feelvision.data.settings.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "feelvision.db")
            .fallbackToDestructiveMigration().build()

    @Provides
    fun providePeopleDao(db: AppDatabase): PeopleDao = db.peopleDao()
}

@Module @InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindPeople  (impl: PeopleRepositoryImpl)  : PeopleRepository
    @Binds @Singleton abstract fun bindSettings(impl: SettingsRepositoryImpl): SettingsRepository
}