package com.eatbefore.feature.common

import com.eatbefore.domain.model.ExpiryStatus
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.StorageType
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Presentation model for a stock row. All expiry logic is resolved here (in the
 * ViewModel layer) so composables stay free of business rules.
 */
data class InventoryRowUi(
    val batchId: Long,
    val productName: String,
    val brand: String?,
    val quantity: Double,
    val unit: MeasurementUnit,
    val locationId: Long,
    val locationName: String,
    val locationType: StorageType,
    val expiryStatus: ExpiryStatus,
    val remainingDays: Long?,
    val isOpened: Boolean,
    /** Catalog photo (https), when the product has one. */
    val imageUri: String? = null,
)

/** Maps a domain [InventoryItem] to its row UI model given today and the "soon" window. */
fun InventoryItem.toRowUi(
    today: LocalDate,
    soonThresholdDays: Int,
    determineExpiryStatus: DetermineExpiryStatusUseCase,
): InventoryRowUi {
    val effective = batch.effectiveExpirationDate
    return InventoryRowUi(
        batchId = batch.id,
        productName = product.name,
        brand = product.brand,
        quantity = batch.quantity,
        unit = batch.measurementUnit,
        locationId = location.id,
        locationName = location.name,
        locationType = location.type,
        expiryStatus = determineExpiryStatus.forDate(effective, today, soonThresholdDays),
        remainingDays = effective?.let { ChronoUnit.DAYS.between(today, it) },
        isOpened = batch.openedAt != null,
        imageUri = product.imageUri,
    )
}
