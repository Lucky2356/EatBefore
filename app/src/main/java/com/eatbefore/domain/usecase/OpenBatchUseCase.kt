package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import javax.inject.Inject
import com.eatbefore.domain.repository.InventoryRepository

/**
 * Marks a batch as opened, stamping openedAt and recomputing the use-by-after-opening
 * date. Idempotent: opening an already-open batch is a no-op. Emits an OPENED event.
 */
class OpenBatchUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val calculateAfterOpening: CalculateExpirationAfterOpeningUseCase,
    private val clock: AppClock,
) {

    suspend operator fun invoke(batchId: Long) {
        val batch = inventoryRepository.getBatch(batchId)
            ?: throw IllegalArgumentException("Unknown batch $batchId")
        require(batch.status.isPresent) { "Cannot open a closed batch" }
        if (batch.openedAt != null) return

        val now = clock.now()
        val openedDate = clock.today()
        val calculated = calculateAfterOpening(
            openedDate = openedDate,
            recommendedUseAfterOpeningDays = batch.recommendedUseAfterOpeningDays,
            printedExpiration = batch.expirationDate,
        )

        val updated = batch.copy(
            openedAt = now,
            calculatedExpirationAfterOpening = calculated,
            status = BatchStatus.OPENED,
            updatedAt = now,
        )

        val event = InventoryEvent(
            inventoryBatchId = batchId,
            productId = batch.productId,
            eventType = EventType.OPENED,
            createdAt = now,
        )

        inventoryRepository.updateBatchWithEvent(updated, event)
    }
}
