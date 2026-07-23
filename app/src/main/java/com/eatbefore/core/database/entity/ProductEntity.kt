package com.eatbefore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.ProductSource

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["barcode"]),
        Index(value = ["name"]),
    ],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable across devices; the local [id] is not. See ADR-0004. */
    @ColumnInfo(name = "uuid") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "barcode") val barcode: String?,
    @ColumnInfo(name = "barcode_type") val barcodeType: BarcodeType,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "brand") val brand: String?,
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "package_size") val packageSize: String?,
    @ColumnInfo(name = "measurement_unit") val measurementUnit: MeasurementUnit,
    @ColumnInfo(name = "image_uri") val imageUri: String?,
    @ColumnInfo(name = "source") val source: ProductSource,
    @ColumnInfo(name = "is_user_created") val isUserCreated: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
