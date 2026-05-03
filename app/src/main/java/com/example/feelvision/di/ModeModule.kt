package com.feelvision.di

import com.feelvision.domain.model.AppMode
import com.feelvision.domain.modes.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntKey
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object ModeModule {

    @Provides @Singleton @IntoMap @IntKey(0)
    fun provideDefault (s: DefaultStrategy) : ModeStrategy = s

    @Provides @Singleton @IntoMap @IntKey(1)
    fun provideOcr     (s: OcrStrategy)     : ModeStrategy = s

    @Provides @Singleton @IntoMap @IntKey(2)
    fun provideNavigate(s: NavigateStrategy): ModeStrategy = s

    @Provides @Singleton @IntoMap @IntKey(3)
    fun provideFace    (s: FaceRecStrategy) : ModeStrategy = s

    @Provides @Singleton @IntoMap @IntKey(4)
    fun provideCurrency(s: CurrencyStrategy): ModeStrategy = s

    @Provides @Singleton @IntoMap @IntKey(5)
    fun provideEdu     (s: EducationStrategy)     : ModeStrategy = s

    @Provides @Singleton @IntoMap @IntKey(6)
    fun provideNarrate (s: NarrateStrategy) : ModeStrategy = s

    @Provides @Singleton
    fun provideModeMap(
        raw: Map<Int, @JvmSuppressWildcards ModeStrategy>
    ): Map<AppMode, @JvmSuppressWildcards ModeStrategy> =
        raw.mapKeys { AppMode.fromId(it.key) }
}