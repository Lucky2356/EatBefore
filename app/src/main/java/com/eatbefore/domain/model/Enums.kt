package com.eatbefore.domain.model

/** Lifecycle state of a concrete stock item ([InventoryBatch]). */
enum class BatchStatus {
    ACTIVE,
    OPENED,
    PARTIALLY_USED,
    CONSUMED,
    DISCARDED,
    EXPIRED,
    ARCHIVED,
    ;

    /** Statuses that still represent food physically present at home. */
    val isPresent: Boolean
        get() = this == ACTIVE || this == OPENED || this == PARTIALLY_USED

    /** Statuses that close out a batch (no longer in stock). */
    val isTerminal: Boolean
        get() = this == CONSUMED || this == DISCARDED || this == ARCHIVED
}

/** Types of actions recorded in a batch's history. Every stock mutation emits one. */
enum class EventType {
    ADDED,
    UPDATED,
    OPENED,
    QUANTITY_CHANGED,
    MOVED,
    CONSUMED,
    DISCARDED,
    EXPIRED,
    RESTORED,
    ADDED_TO_SHOPPING_LIST,
    REMOVED_FROM_SHOPPING_LIST,
}

/** Symbology of a scanned code. Kept separate from parsing so raw codes stay data. */
enum class BarcodeType {
    EAN_13,
    EAN_8,
    UPC_A,
    UPC_E,
    QR,
    DATA_MATRIX,
    CODE_128,
    OTHER,
    NONE,
}

/** Physical kind of a storage location, used for default icons and grouping. */
enum class StorageType {
    FRIDGE,
    FREEZER,
    CUPBOARD,
    PANTRY,
    OTHER,
}

/** Units in which a quantity can be expressed (simple + detailed modes). */
enum class MeasurementUnit {
    PIECE,
    GRAM,
    KILOGRAM,
    MILLILITER,
    LITER,
    PACKAGE,
    PERCENT,
}

/** Where a product card's data originated. */
enum class ProductSource {
    USER,
    SCAN_CACHE,
    EXTERNAL_CATALOG,
    TEMPLATE,
}

/** Priority for a shopping list item. */
enum class ShoppingPriority {
    LOW,
    NORMAL,
    HIGH,
}
