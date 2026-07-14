package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BuildAnalyticsUseCaseTest {

    private val useCase = BuildAnalyticsUseCase()
    private val from: Instant = Instant.parse("2026-07-01T00:00:00Z")

    private fun event(type: EventType, productId: Long = 1, at: String = "2026-07-10T10:00:00Z") =
        InventoryEvent(
            inventoryBatchId = 1,
            productId = productId,
            eventType = type,
            createdAt = Instant.parse(at),
        )

    private val products = mapOf(
        1L to Product(id = 1, name = "Milk", category = "Dairy"),
        2L to Product(id = 2, name = "Bread", category = "Bakery"),
        3L to Product(id = 3, name = "Mystery"),
    )

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
    }
}
