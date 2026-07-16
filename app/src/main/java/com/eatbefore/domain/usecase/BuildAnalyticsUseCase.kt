package com.eatbefore.domain.usecase

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
 * caller supplies the event snapshot and a product lookup map. No monetary analytics:
 * prices are optional in this app, so pseudo-precise money totals would mislead.
 */
class BuildAnalyticsUseCase @Inject constructor() {

    operator fun invoke(
        events: List<InventoryEvent>,
        productsById: Map<Long, Product>,
        from: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): AnalyticsSummary {
        val relevant = events.filter { !it.createdAt.isBefore(from) }

        var added = 0
        var consumed = 0
        var discarded = 0
        var expired = 0
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
                }

                else -> Unit
            }
        }

        // Contiguous weeks (including empty ones) so trend bars are evenly spaced in time.
        val weeklyTrend = buildList {
            val lastWeek = (addedByWeek.keys + wastedByWeek.keys).maxOrNull()
            if (lastWeek != null) {
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
        }

        return AnalyticsSummary(
            weeklyTrend = weeklyTrend,
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

    companion object {
        const val UNCATEGORIZED = ""
        const val TREND_WEEKS = 8
        private const val TOP_LIMIT = 5
    }
}
