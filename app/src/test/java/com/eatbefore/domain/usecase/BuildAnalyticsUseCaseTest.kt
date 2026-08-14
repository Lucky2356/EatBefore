package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.BatchPrice
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BuildAnalyticsUseCaseTest {

    private val useCase = BuildAnalyticsUseCase()
    private val from: Instant = Instant.parse("2026-07-01T00:00:00Z")

    private fun event(
        type: EventType,
        productId: Long = 1,
        at: String = "2026-07-10T10:00:00Z",
        batchId: Long = 1,
    ) = InventoryEvent(
        inventoryBatchId = batchId,
        productId = productId,
        eventType = type,
        createdAt = Instant.parse(at),
    )

    private val products = mapOf(
        1L to Product(id = 1, name = "Milk", category = "Dairy"),
        2L to Product(id = 2, name = "Bread", category = "Bakery"),
        3L to Product(id = 3, name = "Mystery"),
    )

    /**
     * The number that makes waste feel like waste. It has to arrive with its coverage:
     * prices are optional, so a bare total would read as the whole loss.
     */
    @Test
    fun wastedMoney_sumsOnlyWrittenOffBatchesThatHaveAPrice() {
        val events = listOf(
            event(EventType.DISCARDED, batchId = 1),
            event(EventType.EXPIRED, batchId = 2),
            // Written off, but nobody typed a price for it.
            event(EventType.DISCARDED, batchId = 3),
            // Eaten, not wasted — its price is not a loss.
            event(EventType.CONSUMED, batchId = 4),
        )
        val prices = mapOf(
            1L to BatchPrice(100.0, "RUB"),
            2L to BatchPrice(50.0, "RUB"),
            4L to BatchPrice(999.0, "RUB"),
        )

        val money = useCase(events, products, from, ZoneOffset.UTC, prices).wastedMoney!!

        assertEquals(150.0, money.amount, 0.0)
        assertEquals("RUB", money.currency)
        assertEquals(2, money.coveredBatches)
        assertEquals("all three write-offs are the denominator", 3, money.wastedBatches)
    }

    @Test
    fun wastedMoney_isAbsentWhenNothingWastedHadAPrice() {
        val events = listOf(event(EventType.DISCARDED, batchId = 1))

        assertNull(useCase(events, products, from, ZoneOffset.UTC, emptyMap()).wastedMoney)
    }

    /** A price outside the period is not this period's loss. */
    @Test
    fun wastedMoney_ignoresWriteOffsBeforeThePeriod() {
        val events = listOf(
            event(EventType.DISCARDED, batchId = 1, at = "2026-06-01T00:00:00Z"),
            event(EventType.DISCARDED, batchId = 2),
        )
        val prices = mapOf(1L to BatchPrice(100.0, "RUB"), 2L to BatchPrice(7.0, "RUB"))

        val money = useCase(events, products, from, ZoneOffset.UTC, prices).wastedMoney!!

        assertEquals(7.0, money.amount, 0.0)
    }

    @Test
    fun countsEventTypesWithinPeriod() {
        val events = listOf(
            event(EventType.ADDED),
            event(EventType.ADDED, productId = 2),
            event(EventType.CONSUMED),
            event(EventType.DISCARDED, productId = 2),
            event(EventType.EXPIRED, productId = 1),
            // Before the period — must be excluded.
            event(EventType.ADDED, at = "2026-06-01T00:00:00Z"),
        )
        val summary = useCase(events, products, from)
        assertEquals(2, summary.addedCount)
        assertEquals(1, summary.consumedCount)
        assertEquals(1, summary.discardedCount)
        assertEquals(1, summary.expiredCount)
        assertTrue(summary.hasData)
    }

    @Test
    fun wastedByCategory_ranksDescendingAndHandlesMissingCategory() {
        val events = listOf(
            event(EventType.DISCARDED, productId = 2),
            event(EventType.DISCARDED, productId = 2),
            event(EventType.EXPIRED, productId = 1),
            event(EventType.DISCARDED, productId = 3),
        )
        val summary = useCase(events, products, from)
        assertEquals("Bakery" to 2, summary.wastedByCategory.first())
        assertTrue(summary.wastedByCategory.any { it.first == BuildAnalyticsUseCase.UNCATEGORIZED })
    }

    @Test
    fun topAddedProducts_countsPerProduct() {
        val events = listOf(
            event(EventType.ADDED, productId = 1),
            event(EventType.ADDED, productId = 1),
            event(EventType.ADDED, productId = 2),
        )
        val summary = useCase(events, products, from)
        assertEquals("Milk" to 2, summary.topAddedProducts.first())
    }

    @Test
    fun usedInTimePercent_isConsumedShareOfClosed() {
        val events = listOf(
            event(EventType.CONSUMED),
            event(EventType.CONSUMED),
            event(EventType.CONSUMED),
            event(EventType.DISCARDED),
        )
        assertEquals(75, useCase(events, products, from).usedInTimePercent)
    }

    @Test
    fun emptyHistory_hasNoDataAndNullPercent() {
        val summary = useCase(emptyList(), products, from)
        assertFalse(summary.hasData)
        assertNull(summary.usedInTimePercent)
        assertTrue(summary.weeklyTrend.isEmpty())
    }

    @Test
    fun weeklyTrend_groupsByIsoWeekAndFillsGaps() {
        // 2026-07-06 and 2026-07-20 are Mondays; the week between them has no events.
        val events = listOf(
            event(EventType.ADDED, at = "2026-07-07T10:00:00Z"),
            event(EventType.ADDED, at = "2026-07-12T10:00:00Z"), // Sunday, same ISO week
            event(EventType.DISCARDED, at = "2026-07-21T10:00:00Z"),
        )
        val trend = useCase(events, products, from, ZoneOffset.UTC).weeklyTrend

        assertEquals(3, trend.size)
        assertEquals(WeeklyStat(LocalDate.parse("2026-07-06"), added = 2, wasted = 0), trend[0])
        assertEquals(WeeklyStat(LocalDate.parse("2026-07-13"), added = 0, wasted = 0), trend[1])
        assertEquals(WeeklyStat(LocalDate.parse("2026-07-20"), added = 0, wasted = 1), trend[2])
    }

    @Test
    fun weeklyTrend_capsAtTrendWeeks() {
        val events = (0 until 20).map { week ->
            event(
                EventType.ADDED,
                at = Instant.parse("2026-01-05T10:00:00Z")
                    .plusSeconds(week * 7L * 24 * 3600).toString(),
            )
        }
        val trend = useCase(events, products, Instant.EPOCH, ZoneOffset.UTC).weeklyTrend
        assertEquals(BuildAnalyticsUseCase.TREND_WEEKS, trend.size)
    }
}
