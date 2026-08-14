package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.BatchPrice
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.Product
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

private const val PERCENT = 100

/** Added vs wasted counts for one ISO week (Monday-based). */
data class WeeklyStat(val weekStart: LocalDate, val added: Int, val wasted: Int)

/**
 * Money thrown away, and how much of the waste it actually covers.
 *
 * [coveredBatches] out of [wastedBatches] is not a footnote: prices are optional, so a
 * bare "250 ₽" would read as the whole loss when it may be a third of it. The screen shows
 * both numbers or neither.
 */
data class WastedMoney(val amount: Double, val currency: String?, val coveredBatches: Int, val wastedBatches: Int)

/** Aggregated user-facing analytics over the append-only event history. */
data class AnalyticsSummary(
    val addedCount: Int,
    val consumedCount: Int,
    val discardedCount: Int,
    val expiredCount: Int,
    /** Categories most often discarded/expired, with counts, descending. */
    val wastedByCategory: List<Pair<String, Int>>,
    /** Products added most often (regular purchases), with counts, descending. */
    val topAddedProducts: List<Pair<String, Int>>,
    /** Recent weeks (oldest first), capped at [BuildAnalyticsUseCase.TREND_WEEKS]. */
    val weeklyTrend: List<WeeklyStat> = emptyList(),
    /** What the waste cost, when any of it carried a price. Null when none did. */
    val wastedMoney: WastedMoney? = null,
) {
    val hasData: Boolean
        get() = addedCount + consumedCount + discardedCount + expiredCount > 0

    /** Share of closed batches that were used rather than wasted, 0..100; null if none. */
    val usedInTimePercent: Int?
        get() {
            val closed = consumedCount + discardedCount + expiredCount
            return if (closed == 0) null else (consumedCount * PERCENT) / closed
        }
}

/**
 * Builds [AnalyticsSummary] from history events since [from]. Pure and testable — the
 * caller supplies the event snapshot, a product lookup map and whatever prices exist.
 *
 * Money is reported alongside its coverage rather than left out. Leaving it out was the
 * earlier choice, on the grounds that optional prices make a total misleading — but so
 * does a screen that knows a hundred roubles went in the bin and says nothing. Stating
 * "on 3 of 7 written-off packs" keeps the number honest without discarding it.
 */
class BuildAnalyticsUseCase @Inject constructor() {

    operator fun invoke(
        events: List<InventoryEvent>,
        productsById: Map<Long, Product>,
        from: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        pricesByBatchId: Map<Long, BatchPrice> = emptyMap(),
    ): AnalyticsSummary {
        val relevant = events.filter { !it.createdAt.isBefore(from) }

        var added = 0
        var consumed = 0
        var discarded = 0
        var expired = 0
        var wastedAmount = 0.0
        var wastedPriced = 0
        var wastedCurrency: String? = null
        val wastedByCategory = mutableMapOf<String, Int>()
        val addedByProduct = mutableMapOf<Long, Int>()
        val addedByWeek = mutableMapOf<LocalDate, Int>()
        val wastedByWeek = mutableMapOf<LocalDate, Int>()

        fun weekOf(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        for (event in relevant) {
            when (event.eventType) {
                EventType.ADDED -> {
                    added++
                    addedByProduct.merge(event.productId, 1, Int::plus)
                    addedByWeek.merge(weekOf(event.createdAt), 1, Int::plus)
                }

                EventType.CONSUMED -> consumed++

                EventType.DISCARDED, EventType.EXPIRED -> {
                    if (event.eventType == EventType.DISCARDED) discarded++ else expired++
                    val category = productsById[event.productId]?.category
                        ?.takeIf { it.isNotBlank() } ?: UNCATEGORIZED
                    wastedByCategory.merge(category, 1, Int::plus)
                    wastedByWeek.merge(weekOf(event.createdAt), 1, Int::plus)
                    pricesByBatchId[event.inventoryBatchId]?.let { price ->
                        wastedAmount += price.amount
                        wastedPriced++
                        wastedCurrency = wastedCurrency ?: price.currency
                    }
                }

                else -> Unit
            }
        }

        return AnalyticsSummary(
            weeklyTrend = weeklyTrend(addedByWeek, wastedByWeek),
            wastedMoney = if (wastedPriced == 0) {
                null
            } else {
                WastedMoney(
                    amount = wastedAmount,
                    currency = wastedCurrency,
                    coveredBatches = wastedPriced,
                    wastedBatches = discarded + expired,
                )
            },
            addedCount = added,
            consumedCount = consumed,
            discardedCount = discarded,
            expiredCount = expired,
            wastedByCategory = wastedByCategory.entries
                .sortedByDescending { it.value }
                .take(TOP_LIMIT)
                .map { it.key to it.value },
            topAddedProducts = addedByProduct.entries
                .sortedByDescending { it.value }
                .take(TOP_LIMIT)
                .mapNotNull { (productId, count) ->
                    productsById[productId]?.let { it.name to count }
                },
        )
    }

    /**
     * Contiguous weeks, empty ones included, so the trend bars are evenly spaced in time —
     * a quiet week is information, and skipping it would compress the chart into a lie.
     */
    private fun weeklyTrend(
        addedByWeek: Map<LocalDate, Int>,
        wastedByWeek: Map<LocalDate, Int>,
    ): List<WeeklyStat> = buildList {
        val lastWeek = (addedByWeek.keys + wastedByWeek.keys).maxOrNull() ?: return@buildList
        val firstWeek = (addedByWeek.keys + wastedByWeek.keys).min()
            .coerceAtLeast(lastWeek.minusWeeks((TREND_WEEKS - 1).toLong()))
        var week = firstWeek
        while (!week.isAfter(lastWeek)) {
            add(
                WeeklyStat(
                    weekStart = week,
                    added = addedByWeek[week] ?: 0,
                    wasted = wastedByWeek[week] ?: 0,
                ),
            )
            week = week.plusWeeks(1)
        }
    }

    companion object {
        const val UNCATEGORIZED = ""
        const val TREND_WEEKS = 8
        private const val TOP_LIMIT = 5
    }
}
