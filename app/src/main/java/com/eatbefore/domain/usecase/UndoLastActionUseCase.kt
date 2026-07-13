package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.repository.HistoryRepository
import com.eatbefore.domain.repository.InventoryRepository
import javax.inject.Inject

/**
 * Reverses the most recent stock action. History is append-only, so the undo applies the
 * inverse change and records its own compensating event rather than erasing the original.
 * Returns true if something was undone, false if there was nothing to undo.
 *
 * Unsupported event types throw [UnsupportedOperationException] so callers can tell the
 * user the last action cannot be automatically undone.
 */
class UndoLastActionUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val inventoryRepository: InventoryRepository,
    private val clock: AppClock,
) {

    suspend operator fun invoke(): Boolean {
        val last = historyRepository.getLastEvent() ?: return false
        val batch = inventoryRepository.getBatch(last.inventoryBatchId) ?: return false
        val now = clock.now()

        when (last.eventType) {
            EventType.ADDED -> {
                // Undo an addition by archiving (soft-deleting) the batch.
                val updated = batch.copy(
                    status = BatchStatus.ARCHIVED,
                    deletedAt = now,
                    updatedAt = now,
                )
                inventoryRepository.updateBatchWithEvent(
                    updated,
                    compensating(last, EventType.UPDATED, now, reason = "undo add"),
                )
            }

            EventType.QUANTITY_CHANGED, EventType.CONSUMED -> {
                val restoredQty = last.oldQuantity ?: batch.initialQuantity
                val status = when {
                    restoredQty <= 0.0 -> BatchStatus.CONSUMED
                    batch.openedAt != null -> BatchStatus.OPENED
                    restoredQty < batch.initialQuantity -> BatchStatus.PARTIALLY_USED
                    else -> BatchStatus.ACTIVE
                }
                val updated = batch.copy(
                    quantity = restoredQty,
                    status = status,
                    deletedAt = null,
                    updatedAt = now,
                )
                inventoryRepository.updateBatchWithEvent(
                    updated,
                    compensating(last, EventType.QUANTITY_CHANGED, now, newQuantity = restoredQty),
                )
            }

            EventType.DISCARDED, EventType.EXPIRED -> {
                val status = when {
                    batch.openedAt != null -> BatchStatus.OPENED
                    batch.quantity < batch.initialQuantity -> BatchStatus.PARTIALLY_USED
                    else -> BatchStatus.ACTIVE
                }
                val updated = batch.copy(status = status, deletedAt = null, updatedAt = now)
                inventoryRepository.updateBatchWithEvent(
                    updated,
                    compensating(last, EventType.RESTORED, now),
                )
            }

            EventType.MOVED -> {
                val previous = last.previousStorageLocationId
                    ?: throw UnsupportedOperationException("Move has no previous location")
                val updated = batch.copy(storageLocationId = previous, updatedAt = now)
                inventoryRepository.updateBatchWithEvent(
                    updated,
                    compensating(
                        last, EventType.MOVED, now,
                        previousLocation = batch.storageLocationId,
                        newLocation = previous,
                    ),
                )
            }

            EventType.OPENED -> {
                val updated = batch.copy(
                    openedAt = null,
                    calculatedExpirationAfterOpening = null,
                    status = if (batch.quantity < batch.initialQuantity) {
                        BatchStatus.PARTIALLY_USED
                    } else {
                        BatchStatus.ACTIVE
                    },
                    updatedAt = now,
                )
                inventoryRepository.updateBatchWithEvent(
                    updated,
                    compensating(last, EventType.UPDATED, now, reason = "undo open"),
                )
            }

            else -> throw UnsupportedOperationException(
                "Cannot undo ${last.eventType}",
            )
        }
        return true
    }

    private fun compensating(
        source: InventoryEvent,
        type: EventType,
        now: java.time.Instant,
        newQuantity: Double? = null,
        previousLocation: Long? = null,
        newLocation: Long? = null,
        reason: String? = "undo",
    ) = InventoryEvent(
        inventoryBatchId = source.inventoryBatchId,
        productId = source.productId,
        eventType = type,
        newQuantity = newQuantity,
        previousStorageLocationId = previousLocation,
        newStorageLocationId = newLocation,
        reason = reason,
        createdAt = now,
    )
}
