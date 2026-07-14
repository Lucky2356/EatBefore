package com.eatbefore.domain.notification

import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.usecase.BuildExpiryNotificationUseCase
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NotificationLogicTest {

    private val build = BuildExpiryNotificationUseCase(DetermineExpiryStatusUseCase())
    private val today = LocalDate.of(2026, 7, 14)

    private fun item(expiration: LocalDate?): InventoryItem = InventoryItem(
        batch = InventoryBatch(
            id = 1, productId = 1, storageLocationId = 1,
            quantity = 1.0, initialQuantity = 1.0, expirationDate = expiration,
        ),
        product = Product(name = "x"),
        location = StorageLocation(name = "Fridge"),
    )

    @Test
    fun countsExpiredTodayAndSoonSeparately() {
        val items = listOf(
            item(today.minusDays(2)), // expired
            item(today),              // today
            item(today.plusDays(1)),  // soon (threshold 3)
            item(today.plusDays(2)),  // soon
            item(today.plusDays(10)), // fresh (ignored)
            item(null),               // no date (ignored)
        )
        val plan = build(items, today, soonThresholdDays = 3)
        assertEquals(1, plan.expiredCount)
        assertEquals(1, plan.todayCount)
        assertEquals(2, plan.soonCount)
        assertEquals(4, plan.total)
        assertTrue(plan.hasContent)
    }

    @Test
    fun emptyWhenNothingRelevant() {
        val plan = build(listOf(item(today.plusDays(30)), item(null)), today, soonThresholdDays = 3)
        assertFalse(plan.hasContent)
        assertEquals(0, plan.total)
    }

    @Test
    fun quietHours_sameDayWindow() {
        assertTrue(isWithinQuietHours(hour = 3, startHour = 1, endHour = 6))
        assertFalse(isWithinQuietHours(hour = 7, startHour = 1, endHour = 6))
    }

    @Test
    fun quietHours_wrapsPastMidnight() {
        assertTrue(isWithinQuietHours(hour = 23, startHour = 22, endHour = 8))
        assertTrue(isWithinQuietHours(hour = 3, startHour = 22, endHour = 8))
        assertFalse(isWithinQuietHours(hour = 9, startHour = 22, endHour = 8))
    }

    @Test
    fun quietHours_zeroLengthWindowIsNeverQuiet() {
        assertFalse(isWithinQuietHours(hour = 5, startHour = 5, endHour = 5))
    }
}
