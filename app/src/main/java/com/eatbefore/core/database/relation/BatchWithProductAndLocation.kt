package com.eatbefore.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.eatbefore.core.database.entity.InventoryBatchEntity
import com.eatbefore.core.database.entity.ProductEntity
import com.eatbefore.core.database.entity.StorageLocationEntity

/**
 * A batch joined with its product card and storage location. Room populates the nested
 * objects via the [Relation] keys, keeping list queries to a single round trip.
 */
data class BatchWithProductAndLocation(
    @Embedded val batch: InventoryBatchEntity,
    @Relation(parentColumn = "product_id", entityColumn = "id")
    val product: ProductEntity,
    @Relation(parentColumn = "storage_location_id", entityColumn = "id")
    val location: StorageLocationEntity,
)
