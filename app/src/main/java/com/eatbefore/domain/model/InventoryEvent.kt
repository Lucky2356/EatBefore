package com.eatbefore.domain.model

import java.time.Instant

/**
 * An immutable audit record of a single action on a batch. History is append-only:
 * mistakes are corrected by adding compensating events (e.g. RESTORED), never by
 * editing or deleting past ones.
 */
data class InventoryEvent(
    val id: Long = 0,
    /**
     * Stable identity across devices. Carried through the mappers so an ordinary
     * edit keeps the row's identity — regenerating it here would make a peer see the
     * change as a new record. See ADR-0004.
     */
    val uuid: String = "",
    val inventoryBatchId: Long,
    val productId: Long,
    val eventType: EventType,
    val oldQuantity: Double? = null,
    val newQuantity: Double? = null,
    val previousStorageLocationId: Long? = null,
    val newStorageLocationId: Long? = null,
    val reason: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    /** Free-form structured extra data (JSON). Never contains secrets. */
    val metadata: String? = null,
)
