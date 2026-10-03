package com.eatbefore.feature.product

import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.MeasurementUnit
import java.time.LocalDate
import java.time.ZoneId

/** What one purchase of the product cost, per unit of what was bought. */
data class PricePoint(val date: LocalDate, val unitPrice: Double, val currency: String?, val unit: MeasurementUnit)

/**
 * Prices of a product across its purchases, newest first, and how the latest compares.
 *
 * [change] is the latest unit price against the one before it, as a fraction (+0.12 is
 * 12 % dearer); null with fewer than two purchases.
 */
data class PriceHistory(val points: List<PricePoint>, val average: Double, val change: Double?) {
    val latest: PricePoint get() = points.first()
}

/**
 * Builds the price history from every batch ever recorded for the product.
 *
 * A purchase is a day and a currency: a batch split in two in the freezer, or two packs
 * entered separately on the same trip, are one purchase, so their prices and amounts are
 * added back together before dividing. Comparing unit prices rather than totals is what
 * makes «a kilo for 400» and «half a kilo for 220» comparable. Only one currency and one
 * unit are compared — the most recent purchase's — since the rest are not the same number.
 */
fun priceHistoryOf(batches: List<InventoryItem>, zone: ZoneId, limit: Int = MAX_POINTS): PriceHistory? {
    val priced = batches.map { it.batch }
        .filter { (it.price ?: 0.0) > 0.0 && it.initialQuantity > 0.0 }
    if (priced.isEmpty()) return null

    val points = priced
        .groupBy { batch -> Triple(batch.purchaseDate ?: batch.addedAt.atZone(zone).toLocalDate(), batch.currency, batch.measurementUnit) }
        .map { (key, group) ->
            PricePoint(
                date = key.first,
                unitPrice = group.sumOf { it.price ?: 0.0 } / group.sumOf { it.initialQuantity },
                currency = key.second,
                unit = key.third,
            )
        }
        .sortedByDescending { it.date }
    val latest = points.first()
    val comparable = points.filter { it.currency == latest.currency && it.unit == latest.unit }.take(limit)
    val previous = comparable.getOrNull(1)
    return PriceHistory(
        points = comparable,
        average = comparable.map { it.unitPrice }.average(),
        change = previous?.let { (latest.unitPrice - it.unitPrice) / it.unitPrice },
    )
}

private const val MAX_POINTS = 8
