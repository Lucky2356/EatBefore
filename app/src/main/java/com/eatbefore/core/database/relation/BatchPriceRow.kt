package com.eatbefore.core.database.relation

import androidx.room.ColumnInfo

/**
 * What one batch cost, without the rest of it.
 *
 * Analytics counts what was wasted from the event log, but the money lives on the batch,
 * and loading every column of every batch ever recorded to add up a few prices would be a
 * poor trade on a phone.
 */
data class BatchPriceRow(
    @ColumnInfo(name = "id") val batchId: Long,
    @ColumnInfo(name = "price") val price: Double,
    @ColumnInfo(name = "currency") val currency: String?,
)
