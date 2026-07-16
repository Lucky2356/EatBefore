package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.common.validation.InputValidator
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.ProductSource
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.repository.ProductRepository
import com.eatbefore.domain.shelflife.OpeningShelfLife
import java.time.LocalDate
import javax.inject.Inject

/**
 * Adds a product manually. Reuses an existing user-created product card with the same
 * name+brand (so repeat additions don't create duplicates), then inserts a new batch and
 * its ADDED history event atomically.
 */
class AddManualProductUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val mergeSameProduct: MergeSameProductUseCase,
    private val clock: AppClock,
) {

    data class Params(
        val name: String,
        val brand: String? = null,
        val category: String? = null,
        val barcode: String? = null,
        val barcodeType: BarcodeType = BarcodeType.NONE,
        val storageLocationId: Long,
        val quantity: Double = 1.0,
        val measurementUnit: MeasurementUnit = MeasurementUnit.PIECE,
        val expirationDate: LocalDate? = null,
        val recommendedUseAfterOpeningDays: Int? = null,
        val note: String? = null,
        val price: Double? = null,
        val currency: String? = null,
    )

    suspend operator fun invoke(params: Params): Long {
        val name = InputValidator.normalizeProductName(params.name)
        val brand = InputValidator.sanitizeText(params.brand, InputValidator.MAX_BRAND_LENGTH)
        val category =
            InputValidator.sanitizeText(params.category, InputValidator.MAX_CATEGORY_LENGTH)
        val note = InputValidator.sanitizeText(params.note, InputValidator.MAX_NOTE_LENGTH)
        val quantity = InputValidator.clampQuantity(params.quantity)
        require(quantity > 0) { "quantity must be > 0" }
        require(params.storageLocationId > 0) { "storageLocationId is required" }

        val now = clock.now()
        val barcode = InputValidator.sanitizeBarcode(params.barcode)

        val existing = mergeSameProduct.findDuplicate(name = name, brand = brand, barcode = barcode)
        val productId = existing?.id ?: productRepository.upsert(
            Product(
                barcode = barcode,
                barcodeType = if (barcode != null) params.barcodeType else BarcodeType.NONE,
                name = name,
                brand = brand,
                category = category,
                measurementUnit = params.measurementUnit,
                source = ProductSource.USER,
                isUserCreated = true,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val batch = InventoryBatch(
            productId = productId,
            storageLocationId = params.storageLocationId,
            quantity = quantity,
            initialQuantity = quantity,
            measurementUnit = params.measurementUnit,
            addedAt = now,
            expirationDate = params.expirationDate,
            // Rule of thumb for the product group when the caller has nothing better.
            recommendedUseAfterOpeningDays = params.recommendedUseAfterOpeningDays
                ?: OpeningShelfLife.suggestDays(name, category),
            status = BatchStatus.ACTIVE,
            note = note,
            price = params.price,
            currency = InputValidator.sanitizeText(params.currency, InputValidator.MAX_CURRENCY_LENGTH),
            updatedAt = now,
        )

        return inventoryRepository.addBatchWithEvent(batch) { batchId ->
            InventoryEvent(
                inventoryBatchId = batchId,
                productId = productId,
                eventType = EventType.ADDED,
                newQuantity = quantity,
                newStorageLocationId = params.storageLocationId,
                createdAt = now,
            )
        }
    }
}
