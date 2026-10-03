package com.eatbefore.core.backup

import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class StockCsvTest {

    private val labels = StockCsv.Labels(
        headers = listOf(
            "Продукт", "Бренд", "Категория", "Место", "Количество", "Ед.", "Статус",
            "Куплено", "Годен до", "Вскрыто", "Цена", "Валюта", "Заметка",
        ),
        status = { if (it == BatchStatus.ACTIVE) "дома" else "съедено" },
        unit = { "шт" },
    )

    private fun csv(vararg batches: InventoryBatch) = StockCsv.build(
        batches = batches.toList(),
        products = mapOf(1L to Product(id = 1, name = "Сыр; «Российский»", brand = "Брест")),
        locations = mapOf(1L to StorageLocation(id = 1, name = "Холодильник")),
        labels = labels,
        zone = ZoneOffset.UTC,
        locale = Locale.forLanguageTag("ru"),
    )

    @Test
    fun `semicolons, decimal commas, a byte order mark and quoting`() {
        val text = csv(
            InventoryBatch(
                productId = 1, storageLocationId = 1, quantity = 0.5, initialQuantity = 1.0,
                purchaseDate = LocalDate.of(2026, 10, 1), expirationDate = LocalDate.of(2026, 10, 20),
                price = 349.9, currency = "RUB", note = "сказал \"не трогать\"",
            ),
        )
        val lines = text.removePrefix("\uFEFF").trimEnd().split("\r\n")

        assertTrue(text.startsWith("\uFEFF"))
        assertEquals(2, lines.size)
        assertEquals(
            "\"Сыр; «Российский»\";Брест;;Холодильник;0,5;шт;дома;2026-10-01;2026-10-20;;349,9;RUB;\"сказал \"\"не трогать\"\"\"",
            lines[1],
        )
    }

    /** What is at home comes first; the past follows. */
    @Test
    fun `stock at home is listed before what is gone`() {
        val gone =
            InventoryBatch(
                productId = 1,
                storageLocationId = 1,
                quantity = 0.0,
                initialQuantity = 1.0,
                status = BatchStatus.CONSUMED,
                note = "gone",
            )
        val home = InventoryBatch(productId = 1, storageLocationId = 1, quantity = 1.0, initialQuantity = 1.0, note = "home")

        val lines = csv(gone, home).trimEnd().split("\r\n")

        assertTrue(lines[1].endsWith("home"))
        assertTrue(lines[2].endsWith("gone"))
    }
}
