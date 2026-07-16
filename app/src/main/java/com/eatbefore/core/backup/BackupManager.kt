package com.eatbefore.core.backup

import androidx.room.withTransaction
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.database.EatBeforeDatabase
import com.eatbefore.core.database.entity.InventoryBatchEntity
import com.eatbefore.core.database.entity.InventoryEventEntity
import com.eatbefore.core.database.entity.ProductEntity
import com.eatbefore.core.database.entity.ShoppingListItemEntity
import com.eatbefore.core.database.entity.StorageLocationEntity
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.ProductSource
import com.eatbefore.domain.model.ShoppingPriority
import com.eatbefore.domain.model.StorageType
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a successful import: how many rows were restored. */
data class ImportStats(val products: Int, val batches: Int, val events: Int)

/**
 * Exports the whole local database to a versioned JSON document and restores it back.
 * Import validates the schema version and declared counts (integrity check), then swaps
 * all data atomically inside one Room transaction — a corrupt file changes nothing.
 */
@Singleton
class BackupManager @Inject constructor(private val db: EatBeforeDatabase, private val json: Json, private val clock: AppClock) {

    suspend fun export(): String {
        val locations = db.storageLocationDao().getAll().map { it.toBackup() }
        val products = db.productDao().getAll().map { it.toBackup() }
        val batches = db.inventoryBatchDao().getAll().map { it.toBackup() }
        val events = db.inventoryEventDao().getAll().map { it.toBackup() }
        val shopping = db.shoppingListDao().getAll().map { it.toBackup() }

        val file = BackupFile(
            schemaVersion = BackupFile.CURRENT_SCHEMA_VERSION,
            exportedAtEpochMillis = clock.now().toEpochMilli(),
            counts = BackupCounts(
                locations = locations.size,
                products = products.size,
                batches = batches.size,
                events = events.size,
                shopping = shopping.size,
            ),
            locations = locations,
            products = products,
            batches = batches,
            events = events,
            shopping = shopping,
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /**
     * Replaces all local data with the backup content. Throws [IllegalArgumentException]
     * with a safe message when the file is invalid; the transaction guarantees the
     * database is untouched on failure.
     */
    suspend fun import(content: String): ImportStats {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), content)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid backup file")
        }
        require(file.schemaVersion in 1..BackupFile.CURRENT_SCHEMA_VERSION) {
            "Unsupported backup version ${file.schemaVersion}"
        }
        require(
            file.counts.locations == file.locations.size &&
                file.counts.products == file.products.size &&
                file.counts.batches == file.batches.size &&
                file.counts.events == file.events.size &&
                file.counts.shopping == file.shopping.size,
        ) { "Backup integrity check failed" }

        db.withTransaction {
            // Children first on delete; parents first on insert (FK constraints).
            db.inventoryEventDao().deleteAll()
            db.shoppingListDao().deleteAll()
            db.inventoryBatchDao().deleteAll()
            db.productDao().deleteAll()
            db.storageLocationDao().deleteAll()

            db.storageLocationDao().insertAll(file.locations.map { it.toEntity() })
            db.productDao().insertAll(file.products.map { it.toEntity() })
            db.inventoryBatchDao().insertAll(file.batches.map { it.toEntity() })
            db.inventoryEventDao().insertAll(file.events.map { it.toEntity() })
            db.shoppingListDao().insertAll(file.shopping.map { it.toEntity() })
        }

        return ImportStats(
            products = file.products.size,
            batches = file.batches.size,
            events = file.events.size,
        )
    }

    // --- entity <-> backup DTO mapping -------------------------------------------------

    private fun StorageLocationEntity.toBackup() = BackupLocation(
        id,
        name,
        type.name,
        icon,
        sortOrder,
        isDefault,
        isArchived,
    )

    private fun BackupLocation.toEntity() = StorageLocationEntity(
        id = id,
        name = name,
        type = enumOr(type, StorageType.OTHER),
        icon = icon,
        sortOrder = sortOrder,
        isDefault = isDefault,
        isArchived = isArchived,
    )

    private fun ProductEntity.toBackup() = BackupProduct(
        id, barcode, barcodeType.name, name, brand, category, description, packageSize,
        measurementUnit.name, imageUri, source.name, isUserCreated, createdAt, updatedAt,
    )

    private fun BackupProduct.toEntity() = ProductEntity(
        id = id,
        barcode = barcode,
        barcodeType = enumOr(barcodeType, BarcodeType.NONE),
        name = name,
        brand = brand,
        category = category,
        description = description,
        packageSize = packageSize,
        measurementUnit = enumOr(measurementUnit, MeasurementUnit.PIECE),
        imageUri = imageUri,
        source = enumOr(source, ProductSource.USER),
        isUserCreated = isUserCreated,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun InventoryBatchEntity.toBackup() = BackupBatch(
        id, productId, storageLocationId, quantity, initialQuantity, measurementUnit.name,
        purchaseDate, addedAt, expirationDate, openedAt, recommendedUseAfterOpeningDays,
        calculatedExpirationAfterOpening, status.name, note, price, currency, deletedAt, updatedAt,
    )

    private fun BackupBatch.toEntity() = InventoryBatchEntity(
        id = id,
        productId = productId,
        storageLocationId = storageLocationId,
        quantity = quantity,
        initialQuantity = initialQuantity,
        measurementUnit = enumOr(measurementUnit, MeasurementUnit.PIECE),
        purchaseDate = purchaseDate,
        addedAt = addedAt,
        expirationDate = expirationDate,
        openedAt = openedAt,
        recommendedUseAfterOpeningDays = recommendedUseAfterOpeningDays,
        calculatedExpirationAfterOpening = calculatedExpirationAfterOpening,
        status = enumOr(status, BatchStatus.ACTIVE),
        note = note,
        price = price,
        currency = currency,
        deletedAt = deletedAt,
        updatedAt = updatedAt,
    )

    private fun InventoryEventEntity.toBackup() = BackupEvent(
        id, inventoryBatchId, productId, eventType.name, oldQuantity, newQuantity,
        previousStorageLocationId, newStorageLocationId, reason, createdAt, metadata,
    )

    private fun BackupEvent.toEntity() = InventoryEventEntity(
        id = id,
        inventoryBatchId = inventoryBatchId,
        productId = productId,
        eventType = enumOr(eventType, EventType.UPDATED),
        oldQuantity = oldQuantity,
        newQuantity = newQuantity,
        previousStorageLocationId = previousStorageLocationId,
        newStorageLocationId = newStorageLocationId,
        reason = reason,
        createdAt = createdAt,
        metadata = metadata,
    )

    private fun ShoppingListItemEntity.toBackup() = BackupShoppingItem(
        id, productId, customName, quantity, measurementUnit.name, priority.name,
        isCompleted, addedAt, completedAt, sourceInventoryBatchId,
    )

    private fun BackupShoppingItem.toEntity() = ShoppingListItemEntity(
        id = id,
        productId = productId,
        customName = customName,
        quantity = quantity,
        measurementUnit = enumOr(measurementUnit, MeasurementUnit.PIECE),
        priority = enumOr(priority, ShoppingPriority.NORMAL),
        isCompleted = isCompleted,
        addedAt = addedAt,
        completedAt = completedAt,
        sourceInventoryBatchId = sourceInventoryBatchId,
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
