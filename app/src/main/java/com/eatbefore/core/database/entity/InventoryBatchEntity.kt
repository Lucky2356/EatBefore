package com.eatbefore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.MeasurementUnit

@Entity(
    tableName = "inventory_batches",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["storage_location_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["product_id"]),
        Index(value = ["storage_location_id"]),
        Index(value = ["expiration_date"]),
        Index(value = ["status"]),
        Index(value = ["deleted_at"]),
    ],
)
data class InventoryBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable across devices; the local [id] is not. See ADR-0004. */
    @ColumnInfo(name = "uuid") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "storage_location_id") val storageLocationId: Long,
    @ColumnInfo(name = "quantity") val quantity: Double,
    @ColumnInfo(name = "initial_quantity") val initialQuantity: Double,
    @ColumnInfo(name = "measurement_unit") val measurementUnit: MeasurementUnit,
    @ColumnInfo(name = "purchase_date") val purchaseDate: Long?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "expiration_date") val expirationDate: Long?,
    @ColumnInfo(name = "opened_at") val openedAt: Long?,
    @ColumnInfo(name = "recommended_use_after_opening_days") val recommendedUseAfterOpeningDays: Int?,
    @ColumnInfo(name = "calculated_expiration_after_opening") val calculatedExpirationAfterOpening: Long?,
    @ColumnInfo(name = "status") val status: BatchStatus,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "price") val price: Double?,
    @ColumnInfo(name = "currency") val currency: String?,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
