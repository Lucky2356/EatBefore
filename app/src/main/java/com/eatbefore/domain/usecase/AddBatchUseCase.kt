package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.common.validation.InputValidator
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.repository.ProductRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Adds another batch of an existing product (e.g. "add one more package"). One product can
 * hold many batches with independent expiration dates and locations.
 */
class AddBatchUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val clock: AppClock,
) {

    data class Params(
        val productId: Long,
        val storageLocationId: Long,
        val quantity: Double = 1.0,
        val measurementUnit: MeasurementUnit? = null,
        val expirationDate: LocalDate? = null,
        val recommendedUseAfterOpeningDays: Int? = null,
        val note: String? = null,
        val price: Double? = null,
        val currency: String? = null,
    )

    suspend operator fun invoke(params: Params): Long {
        val product = productRepository.getById(params.productId)
            ?: throw IllegalArgumentException("Unknown product ${params.productId}")
        val quantity = InputValidator.clampQuantity(params.quantity)
        require(quantity > 0) { "quantity must be > 0" }
        require(params.storageLocationId > 0) { "storageLocationId is required" }

        val now = clock.now()
        val unit = params.measurementUnit ?: product.measurementUnit
        val batch = InventoryBatch(
            productId = params.productId,
            storageLocationId = params.storageLocationId,
            quantity = quantity,
            initialQuantity = quantity,
            measurementUnit = unit,
            addedAt = now,
            expirationDate = params.expirationDate,
            recommendedUseAfterOpeningDays = params.recommendedUseAfterOpeningDays,
            status = BatchStatus.ACTIVE,
            note = InputValidator.sanitizeText(params.note, InputValidator.MAX_NOTE_LENGTH),
            price = params.price,
            currency = InputValidator.sanitizeText(params.currency, InputValidator.MAX_CURRENCY_LENGTH),
            updatedAt = now,
        )

        return inventoryRepository.addBatchWithEvent(batch) { batchId ->
            InventoryEvent(
                inventoryBatchId = batchId,
                productId = params.productId,
                eventType = EventType.ADDED,
                newQuantity = quantity,
                newStorageLocationId = params.storageLocationId,
                createdAt = now,
            )
        }
    }
}
