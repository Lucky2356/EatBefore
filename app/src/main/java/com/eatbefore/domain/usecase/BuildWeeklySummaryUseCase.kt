package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.BatchPrice
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.Product
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** The week in one line: what was eaten, what was thrown out and what is coming up. */
data class WeeklySummary(
    val eaten: Int,
    val wasted: Int,
    val wastedMoney: WastedMoney?,
    /** Present batches whose date falls within the next seven days. */
    val dueNextWeek: Int,
)

/**
 * Builds the Sunday-evening summary from the same history the analytics screen reads.
 *
 * The analytics screen answers «how are we doing» for whoever opens it; nobody opens it on
 * a Sunday. A weekly line in the shade is what turns the numbers into a habit — and the
 * week ahead is what to plan the shopping around. A week with nothing to report returns
 * null and posts nothing: an empty summary is a nag.
 */
class BuildWeeklySummaryUseCase @Inject constructor(private val buildAnalytics: BuildAnalyticsUseCase) {

    @Suppress("LongParameterList") // Each input is a separate repository snapshot.
    operator fun invoke(
        events: List<InventoryEvent>,
        productsById: Map<Long, Product>,
        prices: Map<Long, BatchPrice>,
        present: List<InventoryItem>,
        now: Instant,
        zone: ZoneId,
    ): WeeklySummary? {
        val week = buildAnalytics(events, productsById, from = now.minus(WEEK), zone = zone, pricesByBatchId = prices)
        val today = now.atZone(zone).toLocalDate()
        val dueNextWeek = present.count { item ->
            val date = item.batch.effectiveExpirationDate ?: return@count false
            !date.isBefore(today) && !date.isAfter(today.plusDays(DAYS_AHEAD))
        }
        val summary = WeeklySummary(
            eaten = week.consumedCount,
            wasted = week.discardedCount + week.expiredCount,
            wastedMoney = week.wastedMoney,
            dueNextWeek = dueNextWeek,
        )
        return summary.takeIf { it.eaten + it.wasted + it.dueNextWeek > 0 }
    }

    private companion object {
        val WEEK: Duration = Duration.ofDays(7)
        const val DAYS_AHEAD = 7L
    }
}
