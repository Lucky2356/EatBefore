package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.repository.InventoryRepository
import javax.inject.Inject

/**
 * Restores a closed/soft-deleted batch back into present stock (undo an accidental
 * write-off). Clears the soft-delete marker and derives a sensible present status from
 * the remaining quantity and open state. Emits a RESTORED event.
 *
 * A batch finished by counting it down has nothing left to restore — its quantity is
 * zero. It comes back with [quantityIfEmpty] (what it had just before, from the event
 * being undone), or failing that its original amount; before, it came back as a present
 * batch of nothing that sat in the list forever.
 */
class RestoreBatchUseCase @Inject constructor(private val inventoryRepository: InventoryRepository, private val clock: AppClock) {

    suspend operator fun invoke(batchId: Long, quantityIfEmpty: Double? = null) {
        val batch = inventoryRepository.getBatch(batchId)
            ?: throw IllegalArgumentException("Unknown batch $batchId")
        if (batch.status.isPresent && batch.deletedAt == null) return

        val now = clock.now()
        val quantity = if (batch.quantity > 0.0) {
            batch.quantity
        } else {
            quantityIfEmpty?.takeIf { it > 0.0 } ?: batch.initialQuantity
        }
        val restoredStatus = derivePresentStatus(batch.copy(quantity = quantity))
        val updated = batch.copy(
            quantity = quantity,
            status = restoredStatus,
            deletedAt = null,
            updatedAt = now,
        )
        val event = InventoryEvent(
            inventoryBatchId = batchId,
            productId = batch.productId,
            eventType = EventType.RESTORED,
            newQuantity = quantity,
            createdAt = now,
        )
        inventoryRepository.updateBatchWithEvent(updated, event)
    }

    private fun derivePresentStatus(batch: InventoryBatch): BatchStatus = when {
        batch.openedAt != null -> BatchStatus.OPENED
        batch.quantity < batch.initialQuantity -> BatchStatus.PARTIALLY_USED
        else -> BatchStatus.ACTIVE
    }
}
