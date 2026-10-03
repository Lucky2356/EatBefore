package com.eatbefore.feature.calendar

import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarMonthTest {

    private val today = LocalDate.of(2026, 10, 3)

    private fun due(date: LocalDate?, name: String = "x") = InventoryItem(
        batch = InventoryBatch(productId = 1, storageLocationId = 1, quantity = 1.0, initialQuantity = 1.0, expirationDate = date),
        product = Product(name = name),
        location = StorageLocation(name = "Fridge"),
    )

    /** October 2026 starts on a Thursday: the grid opens on Monday 28 September. */
    @Test
    fun `the grid is whole weeks starting on monday`() {
        val month = buildCalendarMonth(YearMonth.of(2026, 10), emptyList(), today)

        assertEquals(LocalDate.of(2026, 9, 28), month.weeks.first().first().date)
        assertEquals(DayOfWeek.SUNDAY, month.weeks.last().last().date.dayOfWeek)
        assertTrue(month.weeks.all { it.size == 7 })
        assertEquals(false, month.weeks.first().first().inMonth)
    }

    @Test
    fun `each day counts what runs out on it`() {
        val items = listOf(due(LocalDate.of(2026, 10, 8)), due(LocalDate.of(2026, 10, 8)), due(null))

        val month = buildCalendarMonth(YearMonth.of(2026, 10), items, today)
        val days = month.weeks.flatten()

        assertEquals(2, days.single { it.date == LocalDate.of(2026, 10, 8) }.batchCount)
        assertEquals(2, days.sumOf { it.batchCount })
        assertTrue(days.single { it.date == today }.isToday)
    }

    @Test
    fun `a day lists its batches by name`() {
        val day = LocalDate.of(2026, 10, 8)
        val items = listOf(due(day, "Сыр"), due(day, "кефир"), due(LocalDate.of(2026, 10, 9), "Хлеб"))

        assertEquals(listOf("кефир", "Сыр"), itemsDueOn(day, items).map { it.product.name })
    }
}
