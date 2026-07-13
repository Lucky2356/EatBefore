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
 */
class RestoreBatchUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val clock: AppClock,
) {

    suspend operator fun invoke(batchId: Long) {
        val batch = inventoryRepository.getBatch(batchId)
            ?: throw IllegalArgumentException("Unknown batch $batchId")
        if (batch.status.isPresent && batch.deletedAt == null) return

        val now = clock.now()
        val restoredStatus = derivePresentStatus(batch)
        val updated = batch.copy(
            status = restoredStatus,
            deletedAt = null,
            updatedAt = now,
        )
        val event = InventoryEvent(
            inventoryBatchId = batchId,
            productId = batch.productId,
            eventType = EventType.RESTORED,
            newQuantity = batch.quantity,
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
