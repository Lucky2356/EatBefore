package com.eatbefore.core.sync

import androidx.room.withTransaction
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.database.EatBeforeDatabase
import com.eatbefore.core.database.entity.InventoryBatchEntity
import com.eatbefore.core.database.entity.InventoryEventEntity
import com.eatbefore.core.database.entity.ProductEntity
import com.eatbefore.core.database.entity.StorageLocationEntity
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.ProductSource
import com.eatbefore.domain.model.StorageType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merges household journals. Pure database work — reading and writing the shared folder
 * is [SyncManager]'s job, so this stays testable without SAF or a device.
 *
 * Conflict rules (ADR-0004):
 * - **Events are immutable.** A uuid already present is skipped, which makes a repeated
 *   sync a no-op and the whole operation safe to retry.
 * - **Batches: last write wins** by `updatedAt`. Quantity, status and deletion all travel
 *   on the batch, so a stale peer cannot resurrect something the other person used up.
 * - **Products: last write wins**, but only for fields a person edits. A product is
 *   reference data; the interesting changes are on batches.
 * - **Locations are matched by name+type**, not uuid: they are few, user-named, and two
 *   phones both having "Холодильник" should mean the same shelf.
 */
@Singleton
class SyncEngine @Inject constructor(private val db: EatBeforeDatabase, private val clock: AppClock) {

    /** Everything this device knows, ready to be written to the shared folder. */
    suspend fun buildOwnJournal(deviceId: String): SyncJournal {
        val products = db.productDao().getAll()
        val batches = db.inventoryBatchDao().getAll()
        val locations = db.storageLocationDao().getAll()
        val events = db.inventoryEventDao().getAll()

        val productUuid = products.associate { it.id to it.uuid }
        val batchUuid = batches.associate { it.id to it.uuid }
        val locationName = locations.associate { it.id to it.name }

        return SyncJournal(
            deviceId = deviceId,
            writtenAtEpochMillis = clock.now().toEpochMilli(),
            locations = locations.map { SyncLocation(name = it.name, type = it.type.name) },
            products = products.map { it.toSync() },
            batches = batches.mapNotNull { batch ->
                batch.toSync(
                    productUuid = productUuid[batch.productId] ?: return@mapNotNull null,
                    locationName = locationName[batch.storageLocationId],
                )
            },
            // Events whose batch or product is already gone cannot be resolved by a peer.
            events = events.mapNotNull { event ->
                SyncEvent(
                    uuid = event.uuid,
                    batchUuid = batchUuid[event.inventoryBatchId] ?: return@mapNotNull null,
                    productUuid = productUuid[event.productId] ?: return@mapNotNull null,
                    eventType = event.eventType.name,
                    oldQuantity = event.oldQuantity,
                    newQuantity = event.newQuantity,
                    reason = event.reason,
                    createdAt = event.createdAt,
                    deviceId = event.deviceId.ifEmpty { deviceId },
                )
            },
        )
    }

    /**
     * Applies one peer's journal. Runs in a single transaction: a malformed or truncated
     * file leaves the database exactly as it was.
     */
    suspend fun merge(journal: SyncJournal): SyncStats = db.withTransaction {
        var productsAdded = 0
        var batchesAdded = 0
        var batchesUpdated = 0
        var eventsAdded = 0

        val locationIds = resolveLocations(journal.locations)
        val productIds = mutableMapOf<String, Long>()

        journal.products.forEach { remote ->
            val existing = db.productDao().getByUuid(remote.uuid)
            if (existing == null) {
                productIds[remote.uuid] = db.productDao().insert(remote.toEntity())
                productsAdded++
            } else {
                productIds[remote.uuid] = existing.id
                if (remote.updatedAt > existing.updatedAt) {
                    db.productDao().update(remote.toEntity().copy(id = existing.id))
                }
            }
        }

        val batchIds = mutableMapOf<String, Long>()
        journal.batches.forEach { remote ->
            val productId = productIds[remote.productUuid]
                ?: db.productDao().getByUuid(remote.productUuid)?.id
                ?: return@forEach
            val locationId = remote.locationName?.let { locationIds[it] }
                ?: db.storageLocationDao().getDefault()?.id
                ?: return@forEach

            val existing = db.inventoryBatchDao().getByUuid(remote.uuid)
            if (existing == null) {
                batchIds[remote.uuid] = db.inventoryBatchDao()
                    .insert(remote.toEntity(productId = productId, locationId = locationId))
                batchesAdded++
            } else {
                batchIds[remote.uuid] = existing.id
                // Older news must never overwrite a newer local change.
                if (remote.updatedAt > existing.updatedAt) {
                    db.inventoryBatchDao().update(
                        remote.toEntity(productId = productId, locationId = locationId)
                            .copy(id = existing.id),
                    )
                    batchesUpdated++
                }
            }
        }

        val known = db.inventoryEventDao().getAllUuids().toHashSet()
        journal.events.forEach { remote ->
            if (!known.add(remote.uuid)) return@forEach
            val batchId = batchIds[remote.batchUuid]
                ?: db.inventoryBatchDao().getByUuid(remote.batchUuid)?.id
                ?: return@forEach
            val productId = productIds[remote.productUuid]
                ?: db.productDao().getByUuid(remote.productUuid)?.id
                ?: return@forEach
            db.inventoryEventDao().insert(remote.toEntity(batchId = batchId, productId = productId))
            eventsAdded++
        }

        SyncStats(
            peersSeen = 1,
            productsAdded = productsAdded,
            batchesAdded = batchesAdded,
            batchesUpdated = batchesUpdated,
            eventsAdded = eventsAdded,
        )
    }

    /** Maps the peer's location names onto local ids, creating the ones we lack. */
    private suspend fun resolveLocations(remote: List<SyncLocation>): Map<String, Long> {
        val existing = db.storageLocationDao().getAll()
        return remote.associate { location ->
            val match = existing.firstOrNull { it.name.equals(location.name, ignoreCase = true) }
            val id = match?.id ?: db.storageLocationDao().insert(
                StorageLocationEntity(
                    name = location.name,
                    type = enumOr(location.type, StorageType.OTHER),
                    icon = null,
                    sortOrder = existing.size,
                    // Only this device decides where things go by default.
                    isDefault = false,
                    isArchived = false,
                ),
            )
            location.name to id
        }
    }

    private fun ProductEntity.toSync() = SyncProduct(
        uuid = uuid,
        barcode = barcode,
        barcodeType = barcodeType.name,
        name = name,
        brand = brand,
        category = category,
        packageSize = packageSize,
        measurementUnit = measurementUnit.name,
        imageUri = imageUri,
        updatedAt = updatedAt,
    )

    private fun SyncProduct.toEntity() = ProductEntity(
        uuid = uuid,
        barcode = barcode,
        barcodeType = enumOr(barcodeType, BarcodeType.OTHER),
        name = name,
        brand = brand,
        category = category,
        description = null,
        packageSize = packageSize,
        measurementUnit = enumOr(measurementUnit, MeasurementUnit.PIECE),
        imageUri = imageUri,
        source = ProductSource.USER,
        isUserCreated = true,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun InventoryBatchEntity.toSync(productUuid: String, locationName: String?) = SyncBatch(
        uuid = uuid,
        productUuid = productUuid,
        locationName = locationName,
        quantity = quantity,
        initialQuantity = initialQuantity,
        measurementUnit = measurementUnit.name,
        addedAt = addedAt,
        expirationDate = expirationDate,
        openedAt = openedAt,
        status = status.name,
        note = note,
        deletedAt = deletedAt,
        updatedAt = updatedAt,
        purchaseDate = purchaseDate,
        recommendedUseAfterOpeningDays = recommendedUseAfterOpeningDays,
        calculatedExpirationAfterOpening = calculatedExpirationAfterOpening,
        price = price,
        currency = currency,
    )

    private fun SyncBatch.toEntity(productId: Long, locationId: Long) = InventoryBatchEntity(
        uuid = uuid,
        productId = productId,
        storageLocationId = locationId,
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

    private fun SyncEvent.toEntity(batchId: Long, productId: Long) = InventoryEventEntity(
        uuid = uuid,
        inventoryBatchId = batchId,
        productId = productId,
        eventType = enumOr(eventType, EventType.UPDATED),
        oldQuantity = oldQuantity,
        newQuantity = newQuantity,
        previousStorageLocationId = null,
        newStorageLocationId = null,
        reason = reason,
        createdAt = createdAt,
        metadata = null,
        deviceId = deviceId,
    )

    /** Unknown enum names come from a newer peer; fall back rather than crash. */
    private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
