package com.eatbefore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eatbefore.domain.model.EventType

@Entity(
    tableName = "inventory_events",
    foreignKeys = [
        ForeignKey(
            entity = InventoryBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventory_batch_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["inventory_batch_id"]),
        Index(value = ["product_id"]),
        Index(value = ["event_type"]),
        Index(value = ["created_at"]),
    ],
)
data class InventoryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable across devices; the local [id] is not. See ADR-0004. */
    @ColumnInfo(name = "uuid") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "inventory_batch_id") val inventoryBatchId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "event_type") val eventType: EventType,
    @ColumnInfo(name = "old_quantity") val oldQuantity: Double?,
    @ColumnInfo(name = "new_quantity") val newQuantity: Double?,
    @ColumnInfo(name = "previous_storage_location_id") val previousStorageLocationId: Long?,
    @ColumnInfo(name = "new_storage_location_id") val newStorageLocationId: Long?,
    @ColumnInfo(name = "reason") val reason: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "metadata") val metadata: String?,
    /**
     * Which device recorded this event.
     *
     * Empty for events written **here** — this device's own id is stamped on when the
     * journal is published, so ordinary writes need not do an async lookup for it. A
     * non-empty value therefore means "arrived from a peer", which is exactly what the
     * merge needs to know. See ADR-0004.
     */
    @ColumnInfo(name = "device_id") val deviceId: String = "",
)
