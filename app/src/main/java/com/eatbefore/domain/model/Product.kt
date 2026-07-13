package com.eatbefore.domain.model

import java.time.Instant

/**
 * A product *card* — the reusable description of a food item (what it is), independent
 * of how many packages are currently at home. Concrete stock is [InventoryBatch].
 */
data class Product(
    val id: Long = 0,
    val barcode: String? = null,
    val barcodeType: BarcodeType = BarcodeType.NONE,
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    val description: String? = null,
    val packageSize: String? = null,
    val measurementUnit: MeasurementUnit = MeasurementUnit.PIECE,
    val imageUri: String? = null,
    val source: ProductSource = ProductSource.USER,
    val isUserCreated: Boolean = true,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
)
