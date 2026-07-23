package com.eatbefore.di

import com.eatbefore.data.catalog.ChainedCatalogProvider
import com.eatbefore.data.catalog.openfoodfacts.OpenFoodFactsCatalogProvider
import com.eatbefore.data.catalog.openfoodfacts.OpenFoodFactsContributor
import com.eatbefore.domain.catalog.CatalogContributor
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

    /**
     * The catalog is a chain so more sources can be added without touching callers.
     *
     * Today it holds a single network source, and that is a finding rather than an
     * oversight: there is no free public Russian product API to add. «Честный знак»
     * refuses external callers (HTTP 451), the Национальный каталог requires a paid key,
     * opengtindb is dead and barcode-list.ru blocks programmatic access. The Open*Facts
     * request itself covers four databases at once (see the provider), which is where the
     * real coverage gain comes from.
     */
    @Provides
    @Singleton
    fun provideProductCatalogProvider(
        openFoodFacts: OpenFoodFactsCatalogProvider,
    ): ProductCatalogProvider = ChainedCatalogProvider(
        providers = listOf(openFoodFacts),
    )

    @Provides
    @Singleton
    fun provideCatalogContributor(impl: OpenFoodFactsContributor): CatalogContributor = impl
}
