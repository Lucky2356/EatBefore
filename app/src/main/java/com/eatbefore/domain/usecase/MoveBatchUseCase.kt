package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.repository.InventoryRepository
import javax.inject.Inject

/**
 * Moves a batch to a different storage location, recording the previous and new location
 * in a MOVED event. No-op if the location is unchanged.
 */
class MoveBatchUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val clock: AppClock,
) {

    suspend operator fun invoke(batchId: Long, newStorageLocationId: Long) {
        require(newStorageLocationId > 0) { "newStorageLocationId is required" }
        val batch = inventoryRepository.getBatch(batchId)
            ?: throw IllegalArgumentException("Unknown batch $batchId")
        if (batch.storageLocationId == newStorageLocationId) return

        val now = clock.now()
        val updated = batch.copy(storageLocationId = newStorageLocationId, updatedAt = now)
        val event = InventoryEvent(
            inventoryBatchId = batchId,
            productId = batch.productId,
            eventType = EventType.MOVED,
            previousStorageLocationId = batch.storageLocationId,
            newStorageLocationId = newStorageLocationId,
            createdAt = now,
        )
        inventoryRepository.updateBatchWithEvent(updated, event)
    }
}
