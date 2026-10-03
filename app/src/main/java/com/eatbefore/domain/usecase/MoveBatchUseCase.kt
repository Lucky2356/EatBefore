package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.repository.InventoryRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.round

/**
 * Moves a batch — or part of it — to another storage location, optionally with a new
 * expiry date.
 *
 * The whole batch moves as before: one MOVED event, previous and new location. Two things
 * were added for the freezer:
 *
 * - **A new date with the move.** Chicken frozen on its last fridge day used to keep that
 *   day and show as expired the next morning. The caller passes [Params.newExpirationDate]
 *   with [Params.changeExpiry]; the old date goes into the event's metadata so undo can put
 *   it back. The date after opening is dropped with it — it was counted from the fridge.
 * - **Part of it.** Half the mince into the freezer, the rest for tonight: [Params.quantity]
 *   splits off a new batch at the new place, and the original keeps the remainder. The
 *   price is shared out by amount, so the waste total stays right whichever half is thrown
 *   out. Undo puts the two back together.
 */
class MoveBatchUseCase @Inject constructor(private val inventoryRepository: InventoryRepository, private val clock: AppClock) {

    data class Params(
        val batchId: Long,
        val newStorageLocationId: Long,
        /** How much to move; null or the whole amount moves the batch itself. */
        val quantity: Double? = null,
        /** Applied only with [changeExpiry]; null then means «no date». */
        val newExpirationDate: LocalDate? = null,
        val changeExpiry: Boolean = false,
    )

    suspend operator fun invoke(batchId: Long, newStorageLocationId: Long) {
        invoke(Params(batchId, newStorageLocationId))
    }

    /** Returns the id of the batch now at the new place. */
    suspend operator fun invoke(params: Params): Long {
        require(params.newStorageLocationId > 0) { "newStorageLocationId is required" }
        val batch = inventoryRepository.getBatch(params.batchId)
            ?: throw IllegalArgumentException("Unknown batch ${params.batchId}")
        val part = params.quantity
        return if (part == null || part >= batch.quantity - EPSILON) {
            moveWhole(batch, params)
        } else {
            require(part > 0.0) { "quantity must be > 0" }
            split(batch, part, params)
        }
    }

    private suspend fun moveWhole(batch: InventoryBatch, params: Params): Long {
        val sameExpiry = !params.changeExpiry || params.newExpirationDate == batch.expirationDate
        if (batch.storageLocationId == params.newStorageLocationId && sameExpiry) return batch.id

        val now = clock.now()
        val updated = batch.copy(storageLocationId = params.newStorageLocationId, updatedAt = now)
            .withExpiry(params)
        val event = InventoryEvent(
            inventoryBatchId = batch.id,
            productId = batch.productId,
            eventType = EventType.MOVED,
            previousStorageLocationId = batch.storageLocationId,
            newStorageLocationId = params.newStorageLocationId,
            createdAt = now,
            metadata = if (params.changeExpiry) MoveMetadata.previousExpiry(batch) else null,
        )
        inventoryRepository.updateBatchWithEvent(updated, event)
        return batch.id
    }

    private suspend fun split(batch: InventoryBatch, part: Double, params: Params): Long {
        val now = clock.now()
        val remaining = batch.quantity - part
        val partPrice = batch.price?.let { price -> money(price * part / batch.initialQuantity.coerceAtLeast(part)) }

        val original = batch.copy(
            quantity = remaining,
            initialQuantity = (batch.initialQuantity - part).coerceAtLeast(remaining),
            price = batch.price?.let { money(it - (partPrice ?: 0.0)) },
            updatedAt = now,
        )
        val moved = batch.copy(
            id = 0,
            uuid = "",
            storageLocationId = params.newStorageLocationId,
            quantity = part,
            initialQuantity = part,
            price = partPrice,
            updatedAt = now,
        ).withExpiry(params)

        inventoryRepository.updateBatchWithEvent(
            original,
            InventoryEvent(
                inventoryBatchId = batch.id,
                productId = batch.productId,
                eventType = EventType.QUANTITY_CHANGED,
                oldQuantity = batch.quantity,
                newQuantity = remaining,
                reason = SPLIT_REASON,
                createdAt = now,
            ),
        )
        return inventoryRepository.addBatchWithEvent(moved) { newId ->
            InventoryEvent(
                inventoryBatchId = newId,
                productId = batch.productId,
                eventType = EventType.MOVED,
                newQuantity = part,
                previousStorageLocationId = batch.storageLocationId,
                newStorageLocationId = params.newStorageLocationId,
                reason = SPLIT_REASON,
                createdAt = now,
                metadata = MoveMetadata.splitFrom(batch.id),
            )
        }
    }

    private fun InventoryBatch.withExpiry(params: Params): InventoryBatch =
        if (params.changeExpiry) {
            copy(expirationDate = params.newExpirationDate, calculatedExpirationAfterOpening = null)
        } else {
            this
        }

    private fun money(value: Double): Double = round(value * CENTS) / CENTS

    companion object {
        /** Marks the two halves of a split in history; not «undo…», so they stay undoable. */
        const val SPLIT_REASON = "split"
        private const val EPSILON = 1e-9
        private const val CENTS = 100.0
    }
}

/**
 * What a MOVED event remembers so undo can reverse more than the location. Kept in the
 * event's free-form metadata column rather than new columns: only undo on this phone
 * reads it, and the exchange does not need it.
 */
object MoveMetadata {
    private const val SPLIT_FROM = "splitFrom="
    private const val PREVIOUS_EXPIRY = "prevExpiry="
    private const val PREVIOUS_AFTER_OPENING = ";prevAfterOpening="

    fun splitFrom(batchId: Long): String = "$SPLIT_FROM$batchId"

    fun previousExpiry(batch: InventoryBatch): String =
        PREVIOUS_EXPIRY + (batch.expirationDate?.toEpochDay()?.toString().orEmpty()) +
            PREVIOUS_AFTER_OPENING + (batch.calculatedExpirationAfterOpening?.toEpochDay()?.toString().orEmpty())

    /** The batch this one was split from, if the event records a split. */
    fun parseSplitFrom(metadata: String?): Long? =
        metadata?.takeIf { it.startsWith(SPLIT_FROM) }?.removePrefix(SPLIT_FROM)?.toLongOrNull()

    /** The dates a move replaced: (printed, after opening), or null if it kept them. */
    fun parsePreviousExpiry(metadata: String?): Pair<LocalDate?, LocalDate?>? {
        if (metadata == null || !metadata.startsWith(PREVIOUS_EXPIRY)) return null
        val (printed, afterOpening) = metadata.removePrefix(PREVIOUS_EXPIRY).split(PREVIOUS_AFTER_OPENING, limit = 2)
            .let { it[0] to it.getOrElse(1) { "" } }
        fun day(text: String) = text.toLongOrNull()?.let(LocalDate::ofEpochDay)
        return day(printed) to day(afterOpening)
    }
}
