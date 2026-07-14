package com.eatbefore.domain.gs1

import java.time.DateTimeException
import java.time.LocalDate

/**
 * Minimal GS1 element-string parser for codes like Russian «Честный знак» DataMatrix:
 * `01<GTIN-14>21<serial>{GS}91<key>{GS}92<crypto>` — and generic GS1 QR/DataMatrix that
 * may carry an expiration date (AI 17) or best-before date (AI 15).
 *
 * The payload is treated strictly as data: only known Application Identifiers are read,
 * unknown ones stop parsing, and nothing from the code is ever executed or opened.
 */
object Gs1Parser {

    private const val GS = '\u001d'

    data class Gs1Data(
        /** GTIN-14 exactly as encoded in AI 01. */
        val gtin14: String,
        /** Expiration (AI 17) or, if absent, best-before (AI 15) date. */
        val expirationDate: LocalDate?,
        val batchLot: String?,
        val serial: String?,
    ) {
        /**
         * GTIN normalized for catalog lookup and storage: GTIN-14 with a single leading
         * zero is the EAN-13 form used by retail barcodes and Open Food Facts.
         */
        val normalizedGtin: String
            get() = if (gtin14.length == 14 && gtin14.startsWith("0")) {
                gtin14.substring(1)
            } else {
                gtin14
            }
    }

    /** Cheap pre-check so plain EAN scans skip GS1 parsing entirely. */
    fun looksLikeGs1(raw: String): Boolean {
        val s = stripPrefix(raw)
        return s.length >= 16 && s.startsWith("01") &&
            s.regionMatchesDigits(2, 14) &&
            // A bare 16..18-digit string is more likely a long numeric code than GS1;
            // require either more payload after the GTIN or a known following AI.
            s.length > 16
    }

    fun parse(raw: String): Gs1Data? {
        val s = stripPrefix(raw)
        if (!looksLikeGs1(s)) return null

        var gtin: String? = null
        var expiry17: LocalDate? = null
        var bestBefore15: LocalDate? = null
        var lot: String? = null
        var serial: String? = null

        var i = 0
        loop@ while (i + 2 <= s.length) {
            if (s[i] == GS) {
                i++
                continue
            }
            when (s.substring(i, i + 2)) {
                "01" -> {
                    if (i + 16 > s.length || !s.regionMatchesDigits(i + 2, 14)) break@loop
                    gtin = s.substring(i + 2, i + 16)
                    i += 16
                }

                "17" -> {
                    if (i + 8 > s.length) break@loop
                    expiry17 = parseYymmdd(s.substring(i + 2, i + 8))
                    i += 8
                }

                "15" -> {
                    if (i + 8 > s.length) break@loop
                    bestBefore15 = parseYymmdd(s.substring(i + 2, i + 8))
                    i += 8
                }

                // Production (11) / packaging (13) dates — parsed length, value unused.
                "11", "13" -> i += 8

                "10" -> {
                    val end = variableEnd(s, i + 2, max = 20)
                    lot = s.substring(i + 2, end)
                    i = end
                }

                "21" -> {
                    val end = variableEnd(s, i + 2, max = 20)
                    serial = s.substring(i + 2, end)
                    i = end
                }

                // Честный знак crypto tail and internal AIs — skip to the next GS.
                "91", "92", "93" -> i = variableEnd(s, i + 2, max = 90)

                else -> break@loop
            }
        }

        val foundGtin = gtin ?: return null
        return Gs1Data(
            gtin14 = foundGtin,
            expirationDate = expiry17 ?: bestBefore15,
            batchLot = lot,
            serial = serial,
        )
    }

    /** Strips FNC1/symbology prefixes some scanners prepend (`]d2`, `]Q3`, leading GS). */
    private fun stripPrefix(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("]d2") || s.startsWith("]Q3") || s.startsWith("]C1")) {
            s = s.substring(3)
        }
        return s.trimStart(GS)
    }

    private fun variableEnd(s: String, from: Int, max: Int): Int {
        val gsIndex = s.indexOf(GS, from)
        val hardEnd = minOf(s.length, from + max)
        return if (gsIndex in from until hardEnd) gsIndex else hardEnd
    }

    /** GS1 date: YYMMDD, where DD=00 means "end of month". Invalid dates return null. */
    private fun parseYymmdd(value: String): LocalDate? {
        if (value.length != 6 || !value.all { it.isDigit() }) return null
        val year = 2000 + value.substring(0, 2).toInt()
        val month = value.substring(2, 4).toInt()
        val day = value.substring(4, 6).toInt()
        return try {
            if (day == 0) {
                val first = LocalDate.of(year, month, 1)
                first.withDayOfMonth(first.lengthOfMonth())
            } else {
                LocalDate.of(year, month, day)
            }
        } catch (e: DateTimeException) {
            null
        }
    }

    private fun String.regionMatchesDigits(from: Int, count: Int): Boolean {
        if (from + count > length) return false
        for (k in from until from + count) {
            if (!this[k].isDigit()) return false
        }
        return true
    }
}
