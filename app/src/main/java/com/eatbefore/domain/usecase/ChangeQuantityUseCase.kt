package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.common.validation.InputValidator
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.repository.InventoryRepository
import javax.inject.Inject

/**
 * Sets a batch's quantity. Reaching zero consumes the batch (soft-deleted, kept for
 * history); a partial amount marks it PARTIALLY_USED (unless already OPENED). Emits a
 * QUANTITY_CHANGED event, or CONSUMED when it hits zero.
 */
class ChangeQuantityUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val clock: AppClock,
) {

    suspend operator fun invoke(batchId: Long, newQuantity: Double, reason: String? = null) {
        val batch = inventoryRepository.getBatch(batchId)
            ?: throw IllegalArgumentException("Unknown batch $batchId")
        require(batch.status.isPresent) { "Cannot change quantity of a closed batch" }

        val clamped = InputValidator.clampQuantity(newQuantity)
        val now = clock.now()
        val reachedZero = clamped <= 0.0

        val newStatus = when {
            reachedZero -> BatchStatus.CONSUMED
            batch.status == BatchStatus.OPENED -> BatchStatus.OPENED
            clamped < batch.initialQuantity -> BatchStatus.PARTIALLY_USED
            else -> BatchStatus.ACTIVE
        }

        val updated = batch.copy(
            quantity = clamped,
            status = newStatus,
            deletedAt = if (reachedZero) now else null,
            updatedAt = now,
        )

        val event = InventoryEvent(
            inventoryBatchId = batchId,
            productId = batch.productId,
            eventType = if (reachedZero) EventType.CONSUMED else EventType.QUANTITY_CHANGED,
            oldQuantity = batch.quantity,
            newQuantity = clamped,
            reason = InputValidator.sanitizeText(reason, InputValidator.MAX_NOTE_LENGTH),
            createdAt = now,
        )

        inventoryRepository.updateBatchWithEvent(updated, event)
    }
}
