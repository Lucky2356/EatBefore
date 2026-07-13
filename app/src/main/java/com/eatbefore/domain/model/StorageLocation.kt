package com.eatbefore.domain.model

/** A place where food is kept (fridge, freezer, cupboard, pantry, or custom). */
data class StorageLocation(
    val id: Long = 0,
    val name: String,
    val type: StorageType = StorageType.OTHER,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
)
