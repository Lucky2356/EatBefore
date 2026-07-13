package com.eatbefore.di

import com.eatbefore.core.common.dispatcher.DefaultDispatcher
import com.eatbefore.core.common.dispatcher.IoDispatcher
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.common.time.SystemAppClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideAppClock(): AppClock = SystemAppClock()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
