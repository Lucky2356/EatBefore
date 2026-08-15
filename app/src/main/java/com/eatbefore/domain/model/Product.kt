package com.eatbefore.domain.model

import java.time.Instant

/**
 * A product *card* — the reusable description of a food item (what it is), independent
 * of how many packages are currently at home. Concrete stock is [InventoryBatch].
 */
data class Product(
    val id: Long = 0,
    /**
     * Stable identity across devices. Carried through the mappers so an ordinary
     * edit keeps the row's identity — regenerating it here would make a peer see the
     * change as a new record. See ADR-0004.
     */
    val uuid: String = "",
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
    /**
     * Set when the card was struck off the catalogue. It keeps its history and comes back
     * if the same thing is bought again; null means it is in use.
     */
    val deletedAt: Instant? = null,
)
