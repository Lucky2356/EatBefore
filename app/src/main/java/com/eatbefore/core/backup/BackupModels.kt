package com.eatbefore.core.backup

import kotlinx.serialization.Serializable

/**
 * On-disk backup format. Versioned so future schema changes stay importable; counts act
 * as an integrity check (a truncated or hand-edited file fails validation). Contains no
 * secrets or keys — only the user's own inventory data.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAtEpochMillis: Long,
    val counts: BackupCounts,
    val locations: List<BackupLocation>,
    val products: List<BackupProduct>,
    val batches: List<BackupBatch>,
    val events: List<BackupEvent>,
    val shopping: List<BackupShoppingItem>,
    /**
     * App settings. Added in schema v2 and nullable so v1 files still import — a missing
     * block simply leaves the current settings alone. Never contains the Open Food Facts
     * password: that secret is bound to this device's Keystore and must not travel.
     */
    val settings: BackupSettings? = null,
) {
    companion object {
        /** v2 added [settings]; v1 files remain importable. */
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

/** User preferences worth carrying to a new device. */
@Serializable
data class BackupSettings(
    val soonThresholdDays: Int,
    val detailedQuantityMode: Boolean,
    val themeMode: String,
    val dynamicColors: Boolean,
    val notificationsEnabled: Boolean,
    val notificationHour: Int,
    val notificationMinute: Int,
    val quietHoursEnabled: Boolean,
    val quietStartHour: Int,
    val quietEndHour: Int,
)

@Serializable
data class BackupCounts(val locations: Int, val products: Int, val batches: Int, val events: Int, val shopping: Int)

@Serializable
data class BackupLocation(
    val id: Long,
    val name: String,
    val type: String,
    val icon: String?,
    val sortOrder: Int,
    val isDefault: Boolean,
    val isArchived: Boolean,
)

@Serializable
data class BackupProduct(
    val id: Long,
    /** Stable identity (schema v2+). Empty in v1 files. */
    val uuid: String = "",
    val barcode: String?,
    val barcodeType: String,
    val name: String,
    val brand: String?,
    val category: String?,
    val description: String?,
    val packageSize: String?,
    val measurementUnit: String,
    val imageUri: String?,
    val source: String,
    val isUserCreated: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupBatch(
    val id: Long,
    /** Stable identity (schema v2+). Empty in v1 files. */
    val uuid: String = "",
    val productId: Long,
    val storageLocationId: Long,
    val quantity: Double,
    val initialQuantity: Double,
    val measurementUnit: String,
    val purchaseDate: Long?,
    val addedAt: Long,
    val expirationDate: Long?,
    val openedAt: Long?,
    val recommendedUseAfterOpeningDays: Int?,
    val calculatedExpirationAfterOpening: Long?,
    val status: String,
    val note: String?,
    val price: Double?,
    val currency: String?,
    val deletedAt: Long?,
    val updatedAt: Long,
)

@Serializable
data class BackupEvent(
    val id: Long,
    /** Stable identity (schema v2+). Empty in v1 files. */
    val uuid: String = "",
    val inventoryBatchId: Long,
    val productId: Long,
    val eventType: String,
    val oldQuantity: Double?,
    val newQuantity: Double?,
    val previousStorageLocationId: Long?,
    val newStorageLocationId: Long?,
    val reason: String?,
    val createdAt: Long,
    val metadata: String?,
)

@Serializable
data class BackupShoppingItem(
    val id: Long,
    val productId: Long?,
    val customName: String?,
    val quantity: Double,
    val measurementUnit: String,
    val priority: String,
    val isCompleted: Boolean,
    val addedAt: Long,
    val completedAt: Long?,
    val sourceInventoryBatchId: Long?,
)
