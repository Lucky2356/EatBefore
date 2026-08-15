package com.eatbefore.domain.repository

import com.eatbefore.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    suspend fun getById(id: Long): Product?
    fun observeById(id: Long): Flow<Product?>
    suspend fun getByBarcode(barcode: String): Product?
    suspend fun findUserProductByNameAndBrand(name: String, brand: String?): Product?
    suspend fun upsert(product: Product): Long

    /** Every card, including ones struck off the catalogue — history still needs their names. */
    fun observeAll(): Flow<List<Product>>

    /** The catalogue as the user sees it: cards that can still be chosen. */
    fun observeActive(): Flow<List<Product>>

    /** How many packages of each product are at home right now, keyed by product id. */
    fun observePresentCounts(): Flow<Map<Long, Int>>

    /**
     * Strikes a card off the catalogue, or brings it back with [deleted] = false. The card
     * and its history stay; only the offer to choose it again goes away.
     */
    suspend fun setDeleted(productId: Long, deleted: Boolean)

    /**
     * Most frequently added products (repeat purchases), most frequent first. Only
     * products added at least [minTimes] times qualify, so one-off buys don't show up.
     */
    fun observeFrequent(limit: Int = 6, minTimes: Int = 2): Flow<List<Product>>
}
