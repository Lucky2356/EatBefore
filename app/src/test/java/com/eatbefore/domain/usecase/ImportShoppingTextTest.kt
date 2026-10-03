package com.eatbefore.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportShoppingTextTest {

    @Test
    fun `one item per line, with bullets and checkboxes`() {
        val text = """
            Купить:
            - молоко
            • хлеб
            1. яйца
            [ ] сметана
            ☐ сыр
        """.trimIndent()

        assertEquals(
            // The «Купить:» heading is not an item.
            listOf("молоко", "хлеб", "яйца", "сметана", "сыр"),
            parseShoppingText(text).map { it.first },
        )
    }

    @Test
    fun `commas separate items but not the decimals of a number`() {
        assertEquals(
            listOf("молоко 3,2%", "хлеб", "яйца"),
            parseShoppingText("молоко 3,2%, хлеб,яйца").map { it.first },
        )
    }

    @Test
    fun `a trailing amount becomes the quantity`() {
        assertEquals(
            listOf("яйца" to 2.0, "йогурт" to 4.0, "кефир" to 3.0, "хлеб" to 1.0),
            parseShoppingText("яйца x2\nйогурт 4 шт\nкефир ×3\nхлеб"),
        )
    }

    @Test
    fun `the same thing twice is one line, and blanks are skipped`() {
        assertEquals(listOf("Молоко"), parseShoppingText("Молоко\n\n  \nмолоко").map { it.first })
    }
}
