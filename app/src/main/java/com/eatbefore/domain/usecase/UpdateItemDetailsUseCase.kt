package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.common.validation.InputValidator
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.repository.ProductRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Edits the user-visible details of a stock item: the product card (name, brand,
 * category) and the batch (expiration date, note). The batch change is applied
 * atomically with an UPDATED event; the product card has no event trail by design
 * (only stock movements are history). No-ops when nothing changed.
 */
class UpdateItemDetailsUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val clock: AppClock,
) {

    data class Params(
        val batchId: Long,
        val name: String,
        val brand: String?,
        val category: String?,
        val expirationDate: LocalDate?,
        val note: String?,
    )

    suspend operator fun invoke(params: Params) {
        val batch = inventoryRepository.getBatch(params.batchId)
            ?: throw IllegalArgumentException("Unknown batch ${params.batchId}")
        val product = productRepository.getById(batch.productId)
            ?: throw IllegalArgumentException("Unknown product ${batch.productId}")
        val now = clock.now()

        val name = InputValidator.normalizeProductName(params.name)
        val brand = InputValidator.sanitizeText(params.brand, InputValidator.MAX_BRAND_LENGTH)
        val category = InputValidator.sanitizeText(params.category, InputValidator.MAX_CATEGORY_LENGTH)
        val note = InputValidator.sanitizeText(params.note, InputValidator.MAX_NOTE_LENGTH)

        if (name != product.name || brand != product.brand || category != product.category) {
            productRepository.upsert(
                product.copy(name = name, brand = brand, category = category, updatedAt = now),
            )
        }

        if (params.expirationDate != batch.expirationDate || note != batch.note) {
            val updated = batch.copy(
                expirationDate = params.expirationDate,
                note = note,
                updatedAt = now,
            )
            val event = InventoryEvent(
                inventoryBatchId = batch.id,
                productId = batch.productId,
                eventType = EventType.UPDATED,
                reason = "edit details",
                createdAt = now,
            )
            inventoryRepository.updateBatchWithEvent(updated, event)
        }
    }
}
