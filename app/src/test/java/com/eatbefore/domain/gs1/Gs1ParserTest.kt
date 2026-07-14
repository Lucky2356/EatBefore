package com.eatbefore.domain.gs1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Gs1ParserTest {

    private val gs = '\u001d'.toString()

    @Test
    fun parsesChestnyZnakMilkStyleCode() {
        // Typical «Честный знак» dairy code: 01 GTIN, 21 serial, GS, 93 crypto tail.
        val raw = "010460026601115221JgXJ5.T$gs" + "93dGVz"
        val data = Gs1Parser.parse(raw)!!
        assertEquals("04600266011152", data.gtin14)
        assertEquals("4600266011152", data.normalizedGtin)
        assertEquals("JgXJ5.T", data.serial)
    }

    @Test
    fun parsesExpirationDateAi17() {
        val raw = "010460026601115217270901" + "21SERIAL$gs" + "91EE06"
        val data = Gs1Parser.parse(raw)!!
        assertEquals(LocalDate.of(2027, 9, 1), data.expirationDate)
    }

    @Test
    fun dayZeroMeansEndOfMonth() {
        val raw = "010460026601115217270900" + "21S$gs" + "91EE06"
        val data = Gs1Parser.parse(raw)!!
        assertEquals(LocalDate.of(2027, 9, 30), data.expirationDate)
    }

    @Test
    fun bestBefore15UsedWhenNo17() {
        val raw = "010460026601115215261231" + "21S"
        val data = Gs1Parser.parse(raw)!!
        assertEquals(LocalDate.of(2026, 12, 31), data.expirationDate)
    }

    @Test
    fun batchLotAi10IsParsed() {
        val raw = "0104600266011152" + "10LOT42$gs" + "21S"
        val data = Gs1Parser.parse(raw)!!
        assertEquals("LOT42", data.batchLot)
        assertEquals("S", data.serial)
    }

    @Test
    fun plainEan13IsNotGs1() {
        assertFalse(Gs1Parser.looksLikeGs1("4600266011152"))
        assertNull(Gs1Parser.parse("4600266011152"))
    }

    @Test
    fun urlQrIsNotGs1() {
        assertNull(Gs1Parser.parse("https://example.com/promo"))
    }

    @Test
    fun symbologyPrefixIsStripped() {
        val raw = "]d20104600266011152" + "21ABC"
        val data = Gs1Parser.parse(raw)!!
        assertEquals("04600266011152", data.gtin14)
    }

    @Test
    fun invalidDateInCodeYieldsNullDateButKeepsGtin() {
        val raw = "010460026601115217999999" + "21S"
        val data = Gs1Parser.parse(raw)!!
        assertNull(data.expirationDate)
        assertEquals("04600266011152", data.gtin14)
    }

    @Test
    fun nonZeroLeadingGtin14IsKeptAsIs() {
        val raw = "0194600266011159" + "21ABC"
        val data = Gs1Parser.parse(raw)!!
        assertEquals("94600266011159", data.normalizedGtin)
    }

    @Test
    fun looksLikeGs1_trueForRealCode() {
        assertTrue(Gs1Parser.looksLikeGs1("0104600266011152" + "21JgXJ5.T"))
    }
}
