package com.eatbefore.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpiryDateParserTest {

    private val parser = ExpiryDateParser()
    private val today = LocalDate.of(2026, 7, 14)

    @Test
    fun parsesFullDottedDate() {
        val result = parser.parse("Годен до 20.09.2026", today)
        assertEquals(LocalDate.of(2026, 9, 20), result.first().date)
    }

    @Test
    fun parsesTwoDigitYear() {
        val result = parser.parse("15/08/27", today)
        assertEquals(LocalDate.of(2027, 8, 15), result.first().date)
    }

    @Test
    fun parsesIsoDate() {
        val result = parser.parse("2026-12-31", today)
        assertEquals(LocalDate.of(2026, 12, 31), result.first().date)
    }

    @Test
    fun parsesMonthYearAsEndOfMonth() {
        val result = parser.parse("BB 09.2026", today)
        assertEquals(LocalDate.of(2026, 9, 30), result.first().date)
    }

    @Test
    fun parsesEnglishMonthName() {
        val result = parser.parse("Best before 05 AUG 2026", today)
        assertEquals(LocalDate.of(2026, 8, 5), result.first().date)
    }

    @Test
    fun expiryLabelRanksAboveProductionDate() {
        // Manufacture date earlier line, expiry later — expiry must rank first.
        val text = "Дата изготовления 01.01.2026\nГоден до 01.07.2027"
        val result = parser.parse(text, today)
        assertEquals(LocalDate.of(2027, 7, 1), result.first().date)
    }

    @Test
    fun ignoresImplausiblyOldDates() {
        val result = parser.parse("Дата 01.01.1999", today)
        assertTrue(result.none { it.date.year == 1999 })
    }

    @Test
    fun invalidDate_isSkipped() {
        // 32nd day and 13th month are not valid.
        val result = parser.parse("32.13.2026", today)
        assertTrue(result.isEmpty())
    }

    @Test
    fun blankText_returnsEmpty() {
        assertTrue(parser.parse("   ", today).isEmpty())
    }

    @Test
    fun fullDate_doesNotAlsoEmitEndOfMonthDuplicate() {
        // Only the precise 14.07.2026 should be the top candidate.
        val result = parser.parse("14.07.2026", today)
        assertEquals(LocalDate.of(2026, 7, 14), result.first().date)
        assertTrue(result.first().confidence >= 0.85f)
    }

    @Test
    fun rangeText_multipleCandidatesRankedByConfidence() {
        val result = parser.parse("изг 10.06.2026 годен до 10.12.2026", today)
        // The expiry-labelled date should outrank the production one.
        assertEquals(LocalDate.of(2026, 12, 10), result.first().date)
    }
}
