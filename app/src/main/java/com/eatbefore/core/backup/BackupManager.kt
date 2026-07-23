package com.eatbefore.core.backup

import androidx.room.withTransaction
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.database.EatBeforeDatabase
import com.eatbefore.core.database.entity.InventoryBatchEntity
import com.eatbefore.core.database.entity.InventoryEventEntity
import com.eatbefore.core.database.entity.ProductEntity
import com.eatbefore.core.database.entity.ShoppingListItemEntity
import com.eatbefore.core.database.entity.StorageLocationEntity
import com.eatbefore.core.datastore.ThemeMode
import com.eatbefore.core.datastore.UserPreferences
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.ProductSource
import com.eatbefore.domain.model.ShoppingPriority
import com.eatbefore.domain.model.StorageType
import kotlinx.coroutines.flow.first
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
class BackupManager @Inject constructor(
    private val db: EatBeforeDatabase,
    private val json: Json,
    private val clock: AppClock,
    private val preferences: UserPreferencesRepository,
) {

    /** How an import treats the data already on the device. */
    enum class ImportMode {
        /** Wipe local data and take the file as-is. */
        REPLACE,

        /** Keep local data and add the file's records to it. */
        MERGE,
    }

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
            settings = preferences.preferences.first().toBackupSettings(),
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /**
     * Replaces all local data with the backup content. Throws [IllegalArgumentException]
     * with a safe message when the file is invalid; the transaction guarantees the
     * database is untouched on failure.
     */
    suspend fun import(content: String, mode: ImportMode = ImportMode.REPLACE): ImportStats {
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

        when (mode) {
            ImportMode.REPLACE -> db.withTransaction {
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

            ImportMode.MERGE -> db.withTransaction { mergeInto(file) }
        }

        // Settings live in DataStore, outside the database transaction, so they are
        // applied only after the data import has succeeded. A v1 file carries none.
        file.settings?.let { preferences.restoreFrom(it.toPreferences()) }

        return ImportStats(
            products = file.products.size,
            batches = file.batches.size,
            events = file.events.size,
        )
    }

    /**
     * Adds the file's contents alongside existing data instead of replacing it.
     *
     * Rows carry database-local ids, so every id is remapped: locations and products are
     * matched to existing ones where possible (by type+name and barcode/name+brand) and
     * inserted otherwise; batches, events and list entries are always inserted and
     * re-pointed at the resolved parents.
     *
     * Known limitation: batches have no stable identity across devices, so importing the
     * same file twice adds its batches twice. This is surfaced in the UI and goes away
     * once records carry uuids (see docs/adr/0004-household-sharing.md).
     */
    private suspend fun mergeInto(file: BackupFile) {
        val locationIds = mutableMapOf<Long, Long>()
        val existingLocations = db.storageLocationDao().getAll()
        file.locations.forEach { dto ->
            val entity = dto.toEntity()
            val match = existingLocations.firstOrNull {
                it.name.equals(entity.name, ignoreCase = true) && it.type == entity.type
            }
            locationIds[dto.id] = match?.id
                ?: db.storageLocationDao().insert(entity.copy(id = 0, isDefault = false))
        }

        val productIds = mutableMapOf<Long, Long>()
        file.products.forEach { dto ->
            val entity = dto.toEntity()
            val match = entity.barcode?.let { db.productDao().getByBarcode(it) }
                ?: db.productDao().findUserProductByNameAndBrand(entity.name, entity.brand)
            productIds[dto.id] = match?.id ?: db.productDao().insert(entity.copy(id = 0))
        }

        val batchIds = mutableMapOf<Long, Long>()
        file.batches.forEach { dto ->
            val entity = dto.toEntity()
            val productId = productIds[entity.productId] ?: return@forEach
            val locationId = locationIds[entity.storageLocationId]
                ?: db.storageLocationDao().getDefault()?.id
                ?: return@forEach
            batchIds[dto.id] = db.inventoryBatchDao().insert(
                entity.copy(id = 0, productId = productId, storageLocationId = locationId),
            )
        }

        file.events.forEach { dto ->
            val entity = dto.toEntity()
            val batchId = batchIds[entity.inventoryBatchId] ?: return@forEach
            val productId = productIds[entity.productId] ?: return@forEach
            db.inventoryEventDao().insert(
                entity.copy(
                    id = 0,
                    inventoryBatchId = batchId,
                    productId = productId,
                    previousStorageLocationId = entity.previousStorageLocationId
                        ?.let(locationIds::get),
                    newStorageLocationId = entity.newStorageLocationId?.let(locationIds::get),
                ),
            )
        }

        file.shopping.forEach { dto ->
            val entity = dto.toEntity()
            db.shoppingListDao().insert(
                entity.copy(id = 0, productId = entity.productId?.let(productIds::get)),
            )
        }
    }

    // --- entity <-> backup DTO mapping -------------------------------------------------

    private fun BackupSettings.toPreferences() = UserPreferences(
        soonThresholdDays = soonThresholdDays,
        detailedQuantityMode = detailedQuantityMode,
        themeMode = enumOr(themeMode, ThemeMode.SYSTEM),
        dynamicColors = dynamicColors,
        notificationsEnabled = notificationsEnabled,
        notificationHour = notificationHour,
        notificationMinute = notificationMinute,
        quietHoursEnabled = quietHoursEnabled,
        quietStartHour = quietStartHour,
        quietEndHour = quietEndHour,
    )

    private fun UserPreferences.toBackupSettings() = BackupSettings(
        soonThresholdDays = soonThresholdDays,
        detailedQuantityMode = detailedQuantityMode,
        themeMode = themeMode.name,
        dynamicColors = dynamicColors,
        notificationsEnabled = notificationsEnabled,
        notificationHour = notificationHour,
        notificationMinute = notificationMinute,
        quietHoursEnabled = quietHoursEnabled,
        quietStartHour = quietStartHour,
        quietEndHour = quietEndHour,
    )

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
