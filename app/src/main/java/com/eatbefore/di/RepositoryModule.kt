package com.eatbefore.di

import com.eatbefore.data.ocr.MlKitExpiryDateOcrProvider
import com.eatbefore.data.repository.HistoryRepositoryImpl
import com.eatbefore.data.repository.InventoryRepositoryImpl
import com.eatbefore.data.repository.ProductRepositoryImpl
import com.eatbefore.data.repository.ShoppingListRepositoryImpl
import com.eatbefore.data.repository.StorageLocationRepositoryImpl
import com.eatbefore.domain.ocr.ExpiryDateOcrProvider
import com.eatbefore.domain.repository.HistoryRepository
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.repository.ProductRepository
import com.eatbefore.domain.repository.ShoppingListRepository
import com.eatbefore.domain.repository.StorageLocationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds
    @Singleton
    abstract fun bindStorageLocationRepository(
        impl: StorageLocationRepositoryImpl,
    ): StorageLocationRepository

    @Binds
    @Singleton
    abstract fun bindInventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindShoppingListRepository(
        impl: ShoppingListRepositoryImpl,
    ): ShoppingListRepository

    @Binds
    @Singleton
    abstract fun bindExpiryDateOcrProvider(
        impl: MlKitExpiryDateOcrProvider,
    ): ExpiryDateOcrProvider
}
