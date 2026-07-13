package com.eatbefore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.ShoppingPriority

@Entity(
    tableName = "shopping_list_items",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["is_completed"]),
    ],
)
data class ShoppingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long?,
    @ColumnInfo(name = "custom_name") val customName: String?,
    @ColumnInfo(name = "quantity") val quantity: Double,
    @ColumnInfo(name = "measurement_unit") val measurementUnit: MeasurementUnit,
    @ColumnInfo(name = "priority") val priority: ShoppingPriority,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "source_inventory_batch_id") val sourceInventoryBatchId: Long?,
)
