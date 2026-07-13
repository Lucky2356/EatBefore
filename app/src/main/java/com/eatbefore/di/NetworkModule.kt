package com.eatbefore.di

import com.eatbefore.data.catalog.openfoodfacts.OpenFoodFactsCatalogProvider
import com.eatbefore.domain.catalog.ProductCatalogProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Network setup for the external product catalog. Timeouts are deliberately short so a
 * slow/absent network never blocks the user — the caller falls back to manual entry.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideProductCatalogProvider(
        impl: OpenFoodFactsCatalogProvider,
    ): ProductCatalogProvider = impl
}
