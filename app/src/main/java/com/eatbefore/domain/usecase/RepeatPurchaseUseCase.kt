package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.repository.StorageLocationRepository
import kotlinx.coroutines.flow.first
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * «Купили снова»: the same product, as it was bought last time, in one tap.
 *
 * Bread and milk are bought every few days, and filling in the form each time is the
 * part of the app nobody wants. Last time's purchase already knows everything: how much
 * was bought, where it went, and how long it kept — the days from purchase to its date.
 * The new package gets the same span counted from today, so a two-week yoghurt gets two
 * weeks again rather than last month's date. A product never dated stays undated.
 *
 * The place falls back to the default one when last time's has since been hidden.
 */
class RepeatPurchaseUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val storageLocationRepository: StorageLocationRepository,
    private val addBatch: AddBatchUseCase,
    private val clock: AppClock,
) {

    /** Returns the new batch id, or null when there is no earlier purchase to repeat. */
    suspend operator fun invoke(productId: Long): Long? {
        val last = inventoryRepository.observeAllForProduct(productId).first()
            .maxByOrNull { it.batch.addedAt } ?: return null
        val batch = last.batch
        val today = clock.today()

        val boughtOn = batch.purchaseDate ?: batch.addedAt.atZone(clock.zone()).toLocalDate()
        val keptDays = batch.expirationDate?.let { ChronoUnit.DAYS.between(boughtOn, it) }?.takeIf { it > 0 }
        val location = storageLocationRepository.getById(batch.storageLocationId)
            ?.takeIf { !it.isArchived }
            ?: storageLocationRepository.getDefault()
            ?: return null

        return addBatch(
            AddBatchUseCase.Params(
                productId = productId,
                storageLocationId = location.id,
                quantity = batch.initialQuantity.takeIf { it > 0.0 } ?: 1.0,
                measurementUnit = batch.measurementUnit,
                expirationDate = keptDays?.let { today.plusDays(it) },
            ),
        )
    }
}
