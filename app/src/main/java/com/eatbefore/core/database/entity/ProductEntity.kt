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
    /**
     * When the card was struck off the catalogue; null while it is in use.
     *
     * A mark rather than a `DELETE`, for two reasons. The batches and events of everything
     * ever bought still point here, so the history would lose its names. And the other
     * phone has to be *told* about the deletion: a row that simply vanished looks to it
     * like a row it has and we don't, and the next exchange would hand it straight back.
     */
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    /**
     * Silences the daily expiry reminder for this product, without touching the stock
     * itself: the packets stay in the list and in the analytics, they just stop asking.
     *
     * A property of the card rather than of the phone, so it travels with the card to the
     * other device (ADR-0004) and survives a reinstall in the backup. Muting the yeast
     * that lives in the freezer for a year is a decision about the yeast, not about whose
     * phone happens to be running the reminder.
     */
    @ColumnInfo(name = "notifications_muted") val notificationsMuted: Boolean = false,
)
