package com.eatbefore.domain.ocr

import java.time.DateTimeException
import java.time.LocalDate
import javax.inject.Inject

/**
 * Pure, testable parser that extracts candidate expiration dates from raw OCR text.
 *
 * Handles several common formats (dd.MM.yyyy, dd.MM.yy, MM.yyyy, ISO yyyy-MM-dd, and
 * "dd MON yyyy" with English/Russian month names), assuming a day-first (European /
 * Russian) convention when ambiguous. Confidence is nudged up near "use by" labels and
 * down near "production date" labels so a manufacture date isn't mistaken for expiry.
 *
 * Nothing here decides anything on the user's behalf: it only ranks candidates, which the
 * UI presents for confirmation.
 */
class ExpiryDateParser @Inject constructor() {

    fun parse(rawText: String, today: LocalDate): List<DateCandidate> {
        if (rawText.isBlank()) return emptyList()
        val normalized = rawText.lowercase()
        val candidates = mutableListOf<DateCandidate>()

        for (line in normalized.lines()) {
            candidates += parseLine(line, today)
        }

        // Keep the highest-confidence candidate per distinct date, then rank.
        return candidates
            .groupBy { it.date }
            .map { (_, group) -> group.maxBy { it.confidence } }
            .sortedWith(compareByDescending<DateCandidate> { it.confidence }.thenBy { it.date })
    }

    private fun parseLine(line: String, today: LocalDate): List<DateCandidate> {
        val result = mutableListOf<DateCandidate>()

        NUMERIC_DMY.findAll(line).forEach { m ->
            val (d, mo, y) = Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            buildDate(d.toInt(), mo.toInt(), normalizeYear(y))?.let {
                val base = if (y.length == 4) 0.9f else 0.8f
                result += candidate(it, base, line, m)
            }
        }

        ISO_YMD.findAll(line).forEach { m ->
            buildDate(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
                ?.let { result += candidate(it, 0.85f, line, m) }
        }

        MONTH_YEAR.findAll(line).forEach { m ->
            val month = m.groupValues[1].toInt()
            val year = normalizeYear(m.groupValues[2])
            endOfMonth(year, month)?.let {
                // Month/year only: assume end of month, lower confidence (no day printed).
                result += candidate(it, 0.6f, line, m)
            }
        }

        DAY_MONTHNAME_YEAR.findAll(line).forEach { m ->
            val day = m.groupValues[1].toInt()
            val month = MONTHS[m.groupValues[2].take(3)]
            val year = normalizeYear(m.groupValues[3])
            if (month != null) {
                buildDate(day, month, year)?.let { result += candidate(it, 0.85f, line, m) }
            }
        }

        // Drop implausible dates (far past / far future) to avoid noise from OCR errors.
        return result.filter { it.date >= today.minusYears(2) && it.date <= today.plusYears(15) }
    }

    /** Builds a candidate, nudging confidence by the nearest label before the match. */
    private fun candidate(
        date: LocalDate,
        base: Float,
        line: String,
        match: MatchResult,
    ): DateCandidate {
        val prefix = line.substring(maxOf(0, match.range.first - LABEL_WINDOW), match.range.first)
        val boost = when {
            EXPIRY_LABELS.any { prefix.contains(it) } -> 0.1f
            PRODUCTION_LABELS.any { prefix.contains(it) } -> -0.3f
            else -> 0f
        }
        return DateCandidate(date, clampConfidence(base + boost), match.value.trim())
    }

    private fun normalizeYear(raw: String): Int {
        val n = raw.toInt()
        return if (raw.length == 2) 2000 + n else n
    }

    private fun buildDate(day: Int, month: Int, year: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (e: DateTimeException) {
        null
    }

    private fun endOfMonth(year: Int, month: Int): LocalDate? = try {
        LocalDate.of(year, month, 1).withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth())
    } catch (e: DateTimeException) {
        null
    }

    private fun clampConfidence(value: Float): Float = value.coerceIn(0f, 1f)

    private companion object {
        // dd[sep]mm[sep](yy|yyyy) with . / - or space separators.
        val NUMERIC_DMY = Regex("""\b(\d{1,2})[.\-/ ](\d{1,2})[.\-/ ](\d{4}|\d{2})\b""")

        // ISO yyyy-mm-dd.
        val ISO_YMD = Regex("""\b(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})\b""")

        // mm[sep](yy|yyyy) without a day. Lookbehind/ahead prevent matching the tail of a
        // full dd.mm.yyyy date (e.g. the "07.2026" inside "14.07.2026"), while still
        // allowing a space or label to precede the month.
        val MONTH_YEAR = Regex("""(?<!\d)(?<!\d[.\-/ ])(\d{1,2})[.\-/ ](\d{4}|\d{2})(?![.\-/\d])""")

        const val LABEL_WINDOW = 25

        // dd MON yyyy with a textual month.
        val DAY_MONTHNAME_YEAR =
            Regex("""\b(\d{1,2})[ .\-]?([a-zа-я]{3,})[ .\-]?(\d{4}|\d{2})\b""")

        val EXPIRY_LABELS = listOf(
            "годен до", "годно до", "использовать до", "срок годности", "употребить до",
            "best before", "use by", "exp", "bb", "best by", "до",
        )
        val PRODUCTION_LABELS = listOf(
            "дата изготовления", "изготовлен", "дата производства", "произведено",
            "mfg", "manufactured", "prod", "packed",
        )

        val MONTHS: Map<String, Int> = mapOf(
            // English abbreviations
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
            "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
            // Russian abbreviations (first three letters)
            "янв" to 1, "фев" to 2, "мар" to 3, "апр" to 4, "мая" to 5, "май" to 5,
            "июн" to 6, "июл" to 7, "авг" to 8, "сен" to 9, "окт" to 10, "ноя" to 11,
            "дек" to 12,
        )
    }
}
