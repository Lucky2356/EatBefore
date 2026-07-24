package com.eatbefore.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * A concrete package/portion of a [Product] currently (or previously) at home. One
 * product can have many batches with different expiration dates and locations.
 */
data class InventoryBatch(
    val id: Long = 0,
    /**
     * Stable identity across devices. Carried through the mappers so an ordinary
     * edit keeps the row's identity — regenerating it here would make a peer see the
     * change as a new record. See ADR-0004.
     */
    val uuid: String = "",
    val productId: Long,
    val storageLocationId: Long,
    val quantity: Double,
    val initialQuantity: Double,
    val measurementUnit: MeasurementUnit = MeasurementUnit.PIECE,
    val purchaseDate: LocalDate? = null,
    val addedAt: Instant = Instant.EPOCH,
    val expirationDate: LocalDate? = null,
    val openedAt: Instant? = null,
    val recommendedUseAfterOpeningDays: Int? = null,
    val calculatedExpirationAfterOpening: LocalDate? = null,
    val status: BatchStatus = BatchStatus.ACTIVE,
    val note: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val deletedAt: Instant? = null,
    val updatedAt: Instant = Instant.EPOCH,
) {
    /**
     * Effective expiration is the earlier of the printed date and the
     * use-by-after-opening date, since opening a package usually shortens shelf life.
     */
    val effectiveExpirationDate: LocalDate?
        get() {
            val printed = expirationDate
            val afterOpening = calculatedExpirationAfterOpening
            return when {
                printed == null -> afterOpening
                afterOpening == null -> printed
                else -> if (afterOpening.isBefore(printed)) afterOpening else printed
            }
        }
}
