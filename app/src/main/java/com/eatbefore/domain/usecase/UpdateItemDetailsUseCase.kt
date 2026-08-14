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
        /** What it cost, in [currency]. Null clears it. */
        val price: Double? = null,
        val currency: String? = null,
        /** When it was bought — filled in on adding, correctable here. */
        val purchaseDate: LocalDate? = null,
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

        val price = params.price?.takeIf { it > 0 }
        val currency = InputValidator.sanitizeText(params.currency, InputValidator.MAX_CURRENCY_LENGTH)
        val batchChanged = params.expirationDate != batch.expirationDate ||
            note != batch.note ||
            price != batch.price ||
            params.purchaseDate != batch.purchaseDate

        if (batchChanged) {
            val updated = batch.copy(
                expirationDate = params.expirationDate,
                note = note,
                price = price,
                // A price without a currency cannot be displayed, so they are cleared
                // together. The currency already on the batch wins over the one the caller
                // offers: that one is the phone's default, and a jar bought abroad must
                // not be re-denominated by editing its price at home.
                currency = if (price == null) null else batch.currency ?: currency,
                purchaseDate = params.purchaseDate,
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
