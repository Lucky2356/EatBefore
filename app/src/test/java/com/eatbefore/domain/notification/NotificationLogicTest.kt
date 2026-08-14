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
            id = 1,
            productId = 1,
            storageLocationId = 1,
            quantity = 1.0,
            initialQuantity = 1.0,
            expirationDate = expiration,
        ),
        product = Product(name = "x"),
        location = StorageLocation(name = "Fridge"),
    )

    @Test
    fun countsExpiredTodayAndSoonSeparately() {
        val items = listOf(
            item(today.minusDays(2)), // expired
            item(today), // today
            item(today.plusDays(1)), // soon (threshold 3)
            item(today.plusDays(2)), // soon
            item(today.plusDays(10)), // fresh (ignored)
            item(null), // no date (ignored)
        )
        val plan = build(items, today, soonThresholdDays = 3)
        assertEquals(1, plan.expiredCount)
        assertEquals(1, plan.todayCount)
        assertEquals(2, plan.soonCount)
        assertEquals(4, plan.total)
        assertTrue(plan.hasContent)
    }

    /**
     * The reminder must follow the date the food actually goes off. An opened pack with a
     * distant printed date is the whole point of tracking shelf life after opening — it
     * belongs in the count, and as "expiring soon", not "fresh".
     */
    @Test
    fun opensPackIsCountedByItsAfterOpeningDate() {
        val opened = item(today.plusDays(60)).let { existing ->
            existing.copy(
                batch = existing.batch.copy(
                    openedAt = java.time.Instant.EPOCH,
                    calculatedExpirationAfterOpening = today.plusDays(1),
                ),
            )
        }

        val plan = build(listOf(opened), today, soonThresholdDays = 3)

        assertEquals(1, plan.soonCount)
        assertEquals(0, plan.expiredCount)
    }

    /**
     * With one product the shade can offer "ate it" and "threw it out", so the plan has to
     * say which batch those buttons act on — and name it, since "1 продукт" would send the
     * user into the app to find out which.
     */
    @Test
    fun singleProduct_isNamedSoTheShadeCanActOnIt() {
        val milk = item(today).let {
            it.copy(batch = it.batch.copy(id = 42), product = Product(name = "Молоко"))
        }

        val plan = build(listOf(milk, item(today.plusDays(30))), today, soonThresholdDays = 3)

        assertEquals(42L, plan.singleBatchId)
        assertEquals("Молоко", plan.singleProductName)
    }

    /**
     * With several, the buttons would have to pick one silently. Writing off the wrong
     * carton of milk is worse than making the user open the app.
     */
    @Test
    fun severalProducts_leaveNothingForTheShadeToActOn() {
        val plan = build(listOf(item(today), item(today.minusDays(1))), today, soonThresholdDays = 3)

        assertEquals(null, plan.singleBatchId)
        assertEquals(2, plan.total)
    }

    /** What is fresh or dateless is not part of the reminder, so it cannot be its subject. */
    @Test
    fun theNamedBatchIsOneTheReminderCounted() {
        val plan = build(listOf(item(today.plusDays(60)), item(null)), today, soonThresholdDays = 3)

        assertEquals(null, plan.singleBatchId)
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
