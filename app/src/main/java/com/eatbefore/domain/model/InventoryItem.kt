package com.eatbefore.domain.model

/**
 * A batch together with its product card and storage location — the unit the UI renders
 * in stock lists. Aggregated in the data layer so screens never deal with join details.
 */
data class InventoryItem(
    val batch: InventoryBatch,
    val product: Product,
    val location: StorageLocation,
)
