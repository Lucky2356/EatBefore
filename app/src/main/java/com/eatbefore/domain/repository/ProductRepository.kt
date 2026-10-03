package com.eatbefore.domain.repository

import com.eatbefore.domain.model.HomemadeKind
import com.eatbefore.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    suspend fun getById(id: Long): Product?
    fun observeById(id: Long): Flow<Product?>
    suspend fun getByBarcode(barcode: String): Product?
    suspend fun findUserProductByNameAndBrand(name: String, brand: String?): Product?

    /** A home-made card of the given [kind] with this name, if one was made before. */
    suspend fun findHomemadeProductByName(name: String, kind: HomemadeKind): Product?
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
     * Silences, or restores, the daily expiry reminder for one product. The stock is
     * untouched — only whether it asks to be eaten.
     */
    suspend fun setNotificationsMuted(productId: Long, muted: Boolean)

    /** Keep at least [minQuantity] at home; null removes the minimum. */
    suspend fun setMinQuantity(productId: Long, minQuantity: Double?)

    /** Remind [days] ahead for this product; null returns it to the general setting. */
    suspend fun setReminderDays(productId: Long, days: Int?)

    /**
     * Most frequently added products (repeat purchases), most frequent first. Only
     * products added at least [minTimes] times qualify, so one-off buys don't show up.
     */
    fun observeFrequent(limit: Int = 6, minTimes: Int = 2): Flow<List<Product>>
}
