package com.eatbefore.core.sync

import kotlinx.serialization.Serializable

/**
 * One device's contribution to the shared household journal.
 *
 * Each device writes its **own** file (`journal-<deviceId>.json`) into the shared folder
 * and only ever reads the others. Nobody edits a file they do not own, so two phones
 * writing at the same time cannot corrupt each other — the alternative, one file everyone
 * appends to, has no locking across cloud drives and loses writes.
 *
 * The file is a full snapshot of that device's events plus the products, batches and
 * locations they refer to, so a peer can resolve an event without a prior history.
 */
@Serializable
data class SyncJournal(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val deviceId: String,
    /**
     * What this device calls itself, so a peer can write "Redmi Note 12 threw it out"
     * instead of quoting a uuid at the user. Defaulted rather than versioned: readers
     * ignore unknown keys, so an older app skips it and a newer one reading an older
     * journal simply has no name to show.
     */
    val deviceName: String = "",
    val writtenAtEpochMillis: Long,
    val locations: List<SyncLocation> = emptyList(),
    val products: List<SyncProduct> = emptyList(),
    val batches: List<SyncBatch> = emptyList(),
    val events: List<SyncEvent> = emptyList(),
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val FILE_PREFIX = "journal-"
        const val FILE_SUFFIX = ".json"

        fun fileNameFor(deviceId: String) = "$FILE_PREFIX$deviceId$FILE_SUFFIX"
    }
}

/** Locations are matched by name+type rather than uuid: they are few and user-named. */
@Serializable
data class SyncLocation(val name: String, val type: String)

@Serializable
data class SyncProduct(
    val uuid: String,
    val barcode: String?,
    val barcodeType: String,
    val name: String,
    val brand: String?,
    val category: String?,
    val packageSize: String?,
    val measurementUnit: String,
    val imageUri: String?,
    val updatedAt: Long,
    /**
     * When the other household member struck this card off their catalogue. Defaulted so
     * a journal written by an older version still reads: silence there means "not deleted",
     * which is what those versions meant.
     */
    val deletedAt: Long? = null,
)

@Serializable
data class SyncBatch(
    val uuid: String,
    val productUuid: String,
    val locationName: String?,
    val quantity: Double,
    val initialQuantity: Double,
    val measurementUnit: String,
    val addedAt: Long,
    val expirationDate: Long?,
    val openedAt: Long?,
    val status: String,
    val note: String?,
    val deletedAt: Long?,
    val updatedAt: Long,
    /**
     * Everything below was dropped on the way through the exchange until now: the merge
     * built a batch with these set to null, so the other phone showed an opened pack with
     * no use-by-after-opening deadline and lost the price and purchase date outright.
     * Defaulted so a journal written by an older version still reads.
     */
    val purchaseDate: Long? = null,
    val recommendedUseAfterOpeningDays: Int? = null,
    val calculatedExpirationAfterOpening: Long? = null,
    val price: Double? = null,
    val currency: String? = null,
)

@Serializable
data class SyncEvent(
    val uuid: String,
    val batchUuid: String,
    val productUuid: String,
    val eventType: String,
    val oldQuantity: Double?,
    val newQuantity: Double?,
    val reason: String?,
    val createdAt: Long,
    val deviceId: String,
)

/** What an exchange actually changed, for the settings screen and tests. */
data class SyncStats(
    val peersSeen: Int = 0,
    val productsAdded: Int = 0,
    val batchesAdded: Int = 0,
    val batchesUpdated: Int = 0,
    val eventsAdded: Int = 0,
)
