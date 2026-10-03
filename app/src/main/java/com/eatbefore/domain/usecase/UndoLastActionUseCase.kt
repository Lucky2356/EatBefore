package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.repository.HistoryRepository
import com.eatbefore.domain.repository.InventoryRepository
import java.time.Instant
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
            EventType.ADDED -> undoAdd(batch, last, now)
            EventType.QUANTITY_CHANGED, EventType.CONSUMED -> undoQuantityChange(batch, last, now)
            EventType.DISCARDED, EventType.EXPIRED -> undoWriteOff(batch, last, now)
            EventType.MOVED -> undoMove(batch, last, now)
            EventType.OPENED -> undoOpen(batch, last, now)
            else -> throw UnsupportedOperationException("Cannot undo ${last.eventType}")
        }
        return true
    }

    /** Undo an addition by archiving (soft-deleting) the batch. */
    private suspend fun undoAdd(batch: InventoryBatch, last: InventoryEvent, now: Instant) {
        val updated = batch.copy(status = BatchStatus.ARCHIVED, deletedAt = now, updatedAt = now)
        inventoryRepository.updateBatchWithEvent(
            updated,
            compensating(last, EventType.UPDATED, now, reason = "undo add"),
        )
    }

    private suspend fun undoQuantityChange(
        batch: InventoryBatch,
        last: InventoryEvent,
        now: Instant,
    ) {
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

    /** Restores a batch that was discarded or marked expired. */
    private suspend fun undoWriteOff(batch: InventoryBatch, last: InventoryEvent, now: Instant) {
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

    private suspend fun undoMove(batch: InventoryBatch, last: InventoryEvent, now: Instant) {
        MoveMetadata.parseSplitFrom(last.metadata)?.let { originalId ->
            undoSplit(part = batch, originalId = originalId, last = last, now = now)
            return
        }
        val previous = last.previousStorageLocationId
            ?: throw UnsupportedOperationException("Move has no previous location")
        // A move into or out of the freezer may have set a new date; put the old one back.
        val dates = MoveMetadata.parsePreviousExpiry(last.metadata)
        val moved = batch.copy(storageLocationId = previous, updatedAt = now)
        val updated = if (dates == null) {
            moved
        } else {
            moved.copy(expirationDate = dates.first, calculatedExpirationAfterOpening = dates.second)
        }
        inventoryRepository.updateBatchWithEvent(
            updated,
            compensating(
                last,
                EventType.MOVED,
                now,
                previousLocation = batch.storageLocationId,
                newLocation = previous,
            ),
        )
    }

    /**
     * Puts a split-off part back into the batch it came from: the part is archived, its
     * amount and its share of the price go home. Pressing undo again finds the part already
     * archived and leaves things alone rather than adding the amount a second time.
     */
    private suspend fun undoSplit(part: InventoryBatch, originalId: Long, last: InventoryEvent, now: Instant) {
        if (part.deletedAt != null) return
        val original = inventoryRepository.getBatch(originalId)
            ?: throw UnsupportedOperationException("Split source $originalId is gone")
        val joinedPrice = if (original.price == null && part.price == null) null else (original.price ?: 0.0) + (part.price ?: 0.0)
        inventoryRepository.updateBatchWithEvent(
            part.copy(status = BatchStatus.ARCHIVED, deletedAt = now, updatedAt = now),
            compensating(last, EventType.UPDATED, now, reason = "undo split"),
        )
        inventoryRepository.updateBatchWithEvent(
            original.copy(
                quantity = original.quantity + part.quantity,
                initialQuantity = original.initialQuantity + part.initialQuantity,
                price = joinedPrice,
                updatedAt = now,
            ),
            InventoryEvent(
                inventoryBatchId = original.id,
                productId = original.productId,
                eventType = EventType.QUANTITY_CHANGED,
                oldQuantity = original.quantity,
                newQuantity = original.quantity + part.quantity,
                reason = "undo split",
                createdAt = now,
            ),
        )
    }

    private suspend fun undoOpen(batch: InventoryBatch, last: InventoryEvent, now: Instant) {
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

    private fun compensating(
        source: InventoryEvent,
        type: EventType,
        now: Instant,
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
