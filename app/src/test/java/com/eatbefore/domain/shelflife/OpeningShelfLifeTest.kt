package com.eatbefore.domain.shelflife

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpeningShelfLifeTest {

    @Test
    fun suggestsForCommonDairy() {
        assertEquals(3, OpeningShelfLife.suggestDays("Молоко ультрапастеризованное 3,5 %"))
        assertEquals(7, OpeningShelfLife.suggestDays("Сметана 20 %"))
        assertEquals(5, OpeningShelfLife.suggestDays("Кефир"))
    }

    /** Narrow rules must win over the broad "молоко" rule that follows them. */
    @Test
    fun narrowerRuleWinsOverBroaderOne() {
        assertEquals(14, OpeningShelfLife.suggestDays("Молоко сгущённое с сахаром"))
        assertEquals(30, OpeningShelfLife.suggestDays("Сухое молоко"))
    }

    @Test
    fun matchesEnglishNames() {
        assertEquals(3, OpeningShelfLife.suggestDays("Whole milk"))
        assertEquals(30, OpeningShelfLife.suggestDays("Tomato ketchup"))
    }

    @Test
    fun usesCategoryWhenNameSaysNothing() {
        assertEquals(7, OpeningShelfLife.suggestDays("Российский", category = "Сыры"))
    }

    @Test
    fun matchesIcedTea() {
        assertEquals(3, OpeningShelfLife.suggestDays("Tea nic лимон"))
    }

    @Test
    fun unknownProductGetsNoGuess() {
        assertNull(OpeningShelfLife.suggestDays("Гвозди строительные"))
        assertNull(OpeningShelfLife.suggestDays(null))
        assertNull(OpeningShelfLife.suggestDays("   "))
    }

    @Test
    fun isCaseInsensitive() {
        assertEquals(3, OpeningShelfLife.suggestDays("МОЛОКО"))
    }
}
