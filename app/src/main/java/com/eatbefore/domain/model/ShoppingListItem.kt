package com.eatbefore.domain.model

import java.time.Instant

/** An item on the shopping list. May be linked to a product card or free-typed. */
data class ShoppingListItem(
    val id: Long = 0,
    val productId: Long? = null,
    val customName: String? = null,
    val quantity: Double = 1.0,
    val measurementUnit: MeasurementUnit = MeasurementUnit.PIECE,
    val priority: ShoppingPriority = ShoppingPriority.NORMAL,
    val isCompleted: Boolean = false,
    val addedAt: Instant = Instant.EPOCH,
    val completedAt: Instant? = null,
    val sourceInventoryBatchId: Long? = null,
)
