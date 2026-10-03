package com.eatbefore.feature.product

import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PriceHistoryTest {

    private val zone = ZoneOffset.UTC

    private fun bought(on: LocalDate, price: Double?, quantity: Double = 1.0, unit: MeasurementUnit = MeasurementUnit.PIECE) =
        InventoryItem(
            batch = InventoryBatch(
                productId = 1,
                storageLocationId = 1,
                quantity = quantity,
                initialQuantity = quantity,
                measurementUnit = unit,
                purchaseDate = on,
                price = price,
                currency = "RUB",
            ),
            product = Product(id = 1, name = "Сыр"),
            location = StorageLocation(name = "Fridge"),
        )

    @Test
    fun `nothing priced, no history`() {
        assertNull(priceHistoryOf(listOf(bought(LocalDate.of(2026, 9, 1), price = null)), zone))
    }

    /** Unit prices, newest first, and the latest against the one before it. */
    @Test
    fun `the latest unit price is compared with the previous purchase`() {
        val history = priceHistoryOf(
            listOf(
                bought(LocalDate.of(2026, 8, 1), price = 200.0),
                bought(LocalDate.of(2026, 9, 1), price = 440.0, quantity = 2.0),
            ),
            zone,
        )!!

        assertEquals(220.0, history.latest.unitPrice, 1e-9)
        assertEquals(0.10, history.change!!, 1e-9)
        assertEquals(210.0, history.average, 1e-9)
    }

    /** A batch split in the freezer is still one purchase. */
    @Test
    fun `parts of one purchase are added back together`() {
        val history = priceHistoryOf(
            listOf(
                bought(LocalDate.of(2026, 9, 1), price = 300.0, quantity = 3.0),
                bought(LocalDate.of(2026, 9, 1), price = 100.0, quantity = 1.0),
            ),
            zone,
        )!!

        assertEquals(1, history.points.size)
        assertEquals(100.0, history.latest.unitPrice, 1e-9)
        assertNull(history.change)
    }
}
