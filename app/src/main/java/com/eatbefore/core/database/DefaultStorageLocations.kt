package com.eatbefore.core.database

import com.eatbefore.core.database.entity.StorageLocationEntity
import com.eatbefore.domain.model.StorageType

/**
 * Preset storage locations seeded on first database creation. Names are English keys;
 * the UI maps [StorageType] to a localized label and icon, so seeding is locale-neutral.
 */
object DefaultStorageLocations {
    val entities: List<StorageLocationEntity> = listOf(
        StorageLocationEntity(
            name = "Fridge",
            type = StorageType.FRIDGE,
            icon = "fridge",
            sortOrder = 0,
            isDefault = true,
            isArchived = false,
        ),
        StorageLocationEntity(
            name = "Freezer",
            type = StorageType.FREEZER,
            icon = "freezer",
            sortOrder = 1,
            isDefault = false,
            isArchived = false,
        ),
        StorageLocationEntity(
            name = "Cupboard",
            type = StorageType.CUPBOARD,
            icon = "cupboard",
            sortOrder = 2,
            isDefault = false,
            isArchived = false,
        ),
        StorageLocationEntity(
            name = "Pantry",
            type = StorageType.PANTRY,
            icon = "pantry",
            sortOrder = 3,
            isDefault = false,
            isArchived = false,
        ),
    )
}
