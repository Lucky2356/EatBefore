package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.common.validation.InputValidator
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.repository.InventoryRepository
import javax.inject.Inject

/**
 * Transitions a batch to a closing status (consumed, discarded, expired, archived). The
 * row is never physically deleted — terminal statuses are soft-deleted so history and
 * "restore" keep working. Emits the matching history event.
 */
class MarkBatchStatusUseCase @Inject constructor(private val inventoryRepository: InventoryRepository, private val clock: AppClock) {

    suspend operator fun invoke(
        batchId: Long,
        status: BatchStatus,
        reason: String? = null,
    ) {
        require(status in ALLOWED) { "Status $status is not a closing status" }
        val batch = inventoryRepository.getBatch(batchId)
            ?: throw IllegalArgumentException("Unknown batch $batchId")

        val now = clock.now()
        val updated = batch.copy(
            status = status,
            // Terminal statuses are soft-deleted; EXPIRED is merely excluded from
            // "present" via its status, keeping the item visible for review.
            deletedAt = if (status.isTerminal) now else batch.deletedAt,
            updatedAt = now,
        )

        val event = InventoryEvent(
            inventoryBatchId = batchId,
            productId = batch.productId,
            eventType = EVENT_FOR_STATUS.getValue(status),
            oldQuantity = batch.quantity,
            reason = InputValidator.sanitizeText(reason, InputValidator.MAX_NOTE_LENGTH),
            createdAt = now,
        )

        inventoryRepository.updateBatchWithEvent(updated, event)
    }

    private companion object {
        val ALLOWED = setOf(
            BatchStatus.CONSUMED,
            BatchStatus.DISCARDED,
            BatchStatus.EXPIRED,
            BatchStatus.ARCHIVED,
        )
        val EVENT_FOR_STATUS = mapOf(
            BatchStatus.CONSUMED to EventType.CONSUMED,
            BatchStatus.DISCARDED to EventType.DISCARDED,
            BatchStatus.EXPIRED to EventType.EXPIRED,
            BatchStatus.ARCHIVED to EventType.UPDATED,
        )
    }
}
