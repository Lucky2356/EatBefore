package com.eatbefore.domain.repository

import com.eatbefore.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    suspend fun getById(id: Long): Product?
    fun observeById(id: Long): Flow<Product?>
    suspend fun getByBarcode(barcode: String): Product?
    suspend fun findUserProductByNameAndBrand(name: String, brand: String?): Product?
    suspend fun upsert(product: Product): Long
    fun observeAll(): Flow<List<Product>>
}
