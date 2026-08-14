package com.eatbefore.data.mapper

import com.eatbefore.core.database.entity.InventoryBatchEntity
import com.eatbefore.core.database.entity.InventoryEventEntity
import com.eatbefore.core.database.entity.ProductEntity
import com.eatbefore.core.database.entity.ShoppingListItemEntity
import com.eatbefore.core.database.entity.StorageLocationEntity
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.ShoppingListItem
import com.eatbefore.domain.model.StorageLocation
import java.time.Instant
import java.time.LocalDate

// Entity <-> domain mapping. Kept in the data layer so the domain never sees Room types.

private fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)
private fun Long?.toInstantOrNull(): Instant? = this?.let(Instant::ofEpochMilli)
private fun Long?.toLocalDateOrNull(): LocalDate? = this?.let(LocalDate::ofEpochDay)

fun ProductEntity.toDomain(): Product = Product(
    id = id,
    uuid = uuid,
    barcode = barcode,
    barcodeType = barcodeType,
    name = name,
    brand = brand,
    category = category,
    description = description,
    packageSize = packageSize,
    measurementUnit = measurementUnit,
    imageUri = imageUri,
    source = source,
    isUserCreated = isUserCreated,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

fun Product.toEntity(): ProductEntity = ProductEntity(
    id = id,
    // Blank only for objects built in code before they are stored.
    uuid = uuid.ifBlank { java.util.UUID.randomUUID().toString() },
    barcode = barcode,
    barcodeType = barcodeType,
    name = name,
    brand = brand,
    category = category,
    description = description,
    packageSize = packageSize,
    measurementUnit = measurementUnit,
    imageUri = imageUri,
    source = source,
    isUserCreated = isUserCreated,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun StorageLocationEntity.toDomain(): StorageLocation = StorageLocation(
    id = id,
    name = name,
    type = type,
    icon = icon,
    sortOrder = sortOrder,
    isDefault = isDefault,
    isArchived = isArchived,
)

fun StorageLocation.toEntity(): StorageLocationEntity = StorageLocationEntity(
    id = id,
    name = name,
    type = type,
    icon = icon,
    sortOrder = sortOrder,
    isDefault = isDefault,
    isArchived = isArchived,
)

fun InventoryBatchEntity.toDomain(): InventoryBatch = InventoryBatch(
    id = id,
    uuid = uuid,
    productId = productId,
    storageLocationId = storageLocationId,
    quantity = quantity,
    initialQuantity = initialQuantity,
    measurementUnit = measurementUnit,
    purchaseDate = purchaseDate.toLocalDateOrNull(),
    addedAt = addedAt.toInstant(),
    expirationDate = expirationDate.toLocalDateOrNull(),
    openedAt = openedAt.toInstantOrNull(),
    recommendedUseAfterOpeningDays = recommendedUseAfterOpeningDays,
    calculatedExpirationAfterOpening = calculatedExpirationAfterOpening.toLocalDateOrNull(),
    status = status,
    note = note,
    price = price,
    currency = currency,
    deletedAt = deletedAt.toInstantOrNull(),
    updatedAt = updatedAt.toInstant(),
)

fun InventoryBatch.toEntity(): InventoryBatchEntity = InventoryBatchEntity(
    id = id,
    // Blank only for objects built in code before they are stored.
    uuid = uuid.ifBlank { java.util.UUID.randomUUID().toString() },
    productId = productId,
    storageLocationId = storageLocationId,
    quantity = quantity,
    initialQuantity = initialQuantity,
    measurementUnit = measurementUnit,
    purchaseDate = purchaseDate?.toEpochDay(),
    addedAt = addedAt.toEpochMilli(),
    expirationDate = expirationDate?.toEpochDay(),
    openedAt = openedAt?.toEpochMilli(),
    recommendedUseAfterOpeningDays = recommendedUseAfterOpeningDays,
    calculatedExpirationAfterOpening = calculatedExpirationAfterOpening?.toEpochDay(),
    status = status,
    note = note,
    price = price,
    currency = currency,
    deletedAt = deletedAt?.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun InventoryEventEntity.toDomain(): InventoryEvent = InventoryEvent(
    id = id,
    uuid = uuid,
    inventoryBatchId = inventoryBatchId,
    productId = productId,
    eventType = eventType,
    oldQuantity = oldQuantity,
    newQuantity = newQuantity,
    previousStorageLocationId = previousStorageLocationId,
    newStorageLocationId = newStorageLocationId,
    reason = reason,
    createdAt = createdAt.toInstant(),
    deviceId = deviceId,
    metadata = metadata,
)

fun InventoryEvent.toEntity(): InventoryEventEntity = InventoryEventEntity(
    id = id,
    // Blank only for objects built in code before they are stored.
    uuid = uuid.ifBlank { java.util.UUID.randomUUID().toString() },
    inventoryBatchId = inventoryBatchId,
    productId = productId,
    eventType = eventType,
    oldQuantity = oldQuantity,
    newQuantity = newQuantity,
    previousStorageLocationId = previousStorageLocationId,
    newStorageLocationId = newStorageLocationId,
    reason = reason,
    createdAt = createdAt.toEpochMilli(),
    // Kept rather than defaulted: an event merged from a peer must not lose its author
    // when it passes back through the domain layer.
    deviceId = deviceId,
    metadata = metadata,
)

fun ShoppingListItemEntity.toDomain(): ShoppingListItem = ShoppingListItem(
    id = id,
    productId = productId,
    customName = customName,
    quantity = quantity,
    measurementUnit = measurementUnit,
    priority = priority,
    isCompleted = isCompleted,
    addedAt = addedAt.toInstant(),
    completedAt = completedAt.toInstantOrNull(),
    sourceInventoryBatchId = sourceInventoryBatchId,
)

fun ShoppingListItem.toEntity(): ShoppingListItemEntity = ShoppingListItemEntity(
    id = id,
    productId = productId,
    customName = customName,
    quantity = quantity,
    measurementUnit = measurementUnit,
    priority = priority,
    isCompleted = isCompleted,
    addedAt = addedAt.toEpochMilli(),
    completedAt = completedAt?.toEpochMilli(),
    sourceInventoryBatchId = sourceInventoryBatchId,
)
