package com.eatbefore.core.common.validation

import org.junit.Assert.assertEquals
import org.junit.Test

class InputValidatorTest {

    @Test
    fun normalizeProductName_capitalizesFirstLetter() {
        assertEquals("Молоко", InputValidator.normalizeProductName("молоко"))
        assertEquals("Tea nic лимон", InputValidator.normalizeProductName("tea nic лимон"))
    }

    @Test
    fun normalizeProductName_keepsAlreadyCapitalized() {
        assertEquals("Молоко 3,5 %", InputValidator.normalizeProductName("Молоко 3,5 %"))
    }

    /** Uppercasing beyond the first character would mangle names like these. */
    @Test
    fun normalizeProductName_leavesRestOfNameAlone() {
        assertEquals("IPhone case", InputValidator.normalizeProductName("iPhone case"))
        assertEquals("Eggs (M)", InputValidator.normalizeProductName("eggs (M)"))
        assertEquals("CocaCola", InputValidator.normalizeProductName("CocaCola"))
    }

    @Test
    fun normalizeProductName_trimsAndSanitizesFirst() {
        assertEquals("Сметана", InputValidator.normalizeProductName("  сметана  "))
    }

    /** A name starting with a digit or symbol must not be altered. */
    @Test
    fun normalizeProductName_nonLetterStartIsUnchanged() {
        assertEquals("5 яблок", InputValidator.normalizeProductName("5 яблок"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun normalizeProductName_rejectsBlank() {
        InputValidator.normalizeProductName("   ")
    }
}
