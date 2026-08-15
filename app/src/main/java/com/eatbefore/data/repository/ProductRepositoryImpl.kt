package com.eatbefore.data.repository

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.database.dao.ProductDao
import com.eatbefore.data.mapper.toDomain
import com.eatbefore.data.mapper.toEntity
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProductRepositoryImpl @Inject constructor(private val productDao: ProductDao, private val clock: AppClock) : ProductRepository {

    override suspend fun getById(id: Long): Product? = productDao.getById(id)?.toDomain()

    override fun observeById(id: Long): Flow<Product?> =
        productDao.observeById(id).map { it?.toDomain() }

    override suspend fun getByBarcode(barcode: String): Product? =
        productDao.getByBarcode(barcode)?.toDomain()

    override suspend fun findUserProductByNameAndBrand(name: String, brand: String?): Product? =
        productDao.findUserProductByNameAndBrand(name, brand)?.toDomain()

    override suspend fun upsert(product: Product): Long = if (product.id == 0L) {
        productDao.insert(product.toEntity())
    } else {
        productDao.update(product.toEntity())
        product.id
    }

    override fun observeAll(): Flow<List<Product>> =
        productDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<List<Product>> =
        productDao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observePresentCounts(): Flow<Map<Long, Int>> =
        productDao.observePresentCounts().map { rows -> rows.associate { it.productId to it.count } }

    override suspend fun setDeleted(productId: Long, deleted: Boolean) {
        val now = clock.now().toEpochMilli()
        productDao.setDeletedAt(id = productId, at = if (deleted) now else null, now = now)
    }

    override fun observeFrequent(limit: Int, minTimes: Int): Flow<List<Product>> =
        productDao.observeFrequent(limit, minTimes).map { list -> list.map { it.toDomain() } }
}
