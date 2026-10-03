package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BuildWeeklySummaryUseCaseTest {

    private val build = BuildWeeklySummaryUseCase(BuildAnalyticsUseCase())
    private val now = Instant.parse("2026-10-04T19:00:00Z")
    private val zone = ZoneOffset.UTC

    private fun event(type: EventType, at: String) =
        InventoryEvent(inventoryBatchId = 1, productId = 1, eventType = type, createdAt = Instant.parse(at))

    private fun due(date: LocalDate?) = InventoryItem(
        batch = InventoryBatch(productId = 1, storageLocationId = 1, quantity = 1.0, initialQuantity = 1.0, expirationDate = date),
        product = Product(name = "x"),
        location = StorageLocation(name = "Fridge"),
    )

    @Test
    fun `the last seven days and the next seven`() {
        val events = listOf(
            event(EventType.CONSUMED, "2026-10-01T10:00:00Z"),
            event(EventType.CONSUMED, "2026-10-02T10:00:00Z"),
            event(EventType.DISCARDED, "2026-10-03T10:00:00Z"),
            // Older than a week: not this week's news.
            event(EventType.CONSUMED, "2026-09-20T10:00:00Z"),
        )
        val present = listOf(due(LocalDate.of(2026, 10, 7)), due(LocalDate.of(2026, 10, 30)), due(null))

        val summary = build(events, emptyMap(), emptyMap(), present, now, zone)!!

        assertEquals(2, summary.eaten)
        assertEquals(1, summary.wasted)
        assertEquals(1, summary.dueNextWeek)
    }

    @Test
    fun `a quiet week says nothing`() {
        assertNull(build(emptyList(), emptyMap(), emptyMap(), emptyList(), now, zone))
    }
}
