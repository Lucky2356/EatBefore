package com.eatbefore.domain.shelflife

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The suggestion offered when adding a product by hand. It is a chip the user taps, never
 * a date applied on their behalf, so the bar it has to clear is "plausible enough to be
 * worth one tap" — but a rule that fires on the wrong product is worse than no rule, since
 * the app would then warn about the wrong day with complete confidence.
 */
class TypicalShelfLifeTest {

    @Test
    fun `sealed milk keeps for a week, not the three days an opened one has`() {
        // The number from OpeningShelfLife would be a lie about a carton from the shop.
        assertEquals(7, TypicalShelfLife.suggestDays("Молоко 3,2%"))
        assertEquals(3, OpeningShelfLife.suggestDays("Молоко 3,2%"))
    }

    @Test
    fun `narrow rules win over the broad ones they sit inside`() {
        // "сгущённое молоко" contains "молоко"; a tin is not a carton.
        assertEquals(365, TypicalShelfLife.suggestDays("Молоко сгущенное"))
        assertEquals(180, TypicalShelfLife.suggestDays("Сухое молоко"))
        // "масло" alone would be ambiguous; the two kinds keep very differently.
        assertEquals(365, TypicalShelfLife.suggestDays("Масло растительное"))
        assertEquals(30, TypicalShelfLife.suggestDays("Масло сливочное"))
    }

    @Test
    fun `meat and fish get the shortest suggestion`() {
        assertEquals(2, TypicalShelfLife.suggestDays("Фарш говяжий"))
        assertEquals(2, TypicalShelfLife.suggestDays("Рыба красная"))
    }

    @Test
    fun `pantry staples get a long one`() {
        assertEquals(365, TypicalShelfLife.suggestDays("Гречка"))
        assertEquals(730, TypicalShelfLife.suggestDays("Тушенка"))
    }

    @Test
    fun `English names are matched too`() {
        assertEquals(7, TypicalShelfLife.suggestDays("Fresh milk"))
        assertEquals(4, TypicalShelfLife.suggestDays("Sourdough bread"))
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(7, TypicalShelfLife.suggestDays("МОЛОКО"))
    }

    /** An unknown product gets no chip at all rather than an average of everything. */
    @Test
    fun `an unrecognized product gets no suggestion`() {
        assertNull(TypicalShelfLife.suggestDays("Вкусняшка"))
        assertNull(TypicalShelfLife.suggestDays(""))
        assertNull(TypicalShelfLife.suggestDays(null))
    }

    /** The category helps when the name is a brand nobody can parse. */
    @Test
    fun `the category is used when the name says nothing`() {
        assertEquals(21, TypicalShelfLife.suggestDays("Ламбер", category = "Сыр"))
    }
}
