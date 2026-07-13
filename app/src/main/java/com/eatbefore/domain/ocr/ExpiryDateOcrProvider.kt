package com.eatbefore.domain.ocr

import java.time.LocalDate

/**
 * Abstraction over on-device OCR that extracts candidate expiration dates from a photo of
 * a package. Kept as a domain interface so the OCR engine (ML Kit, added later) is
 * swappable and testable with fakes. Results are always confirmed by the user before use.
 */
interface ExpiryDateOcrProvider {
    /** @param imageUri content URI of a captured/selected image. */
    suspend fun recognize(imageUri: String): OcrResult
}

data class OcrResult(
    /** The raw recognized text, shown to the user for transparency. */
    val rawText: String,
    /** Ranked date candidates parsed from the text; may be empty. */
    val candidates: List<DateCandidate>,
)

data class DateCandidate(
    val date: LocalDate,
    /** 0..1 heuristic confidence. Low confidence must not be presented as certain. */
    val confidence: Float,
    /** The substring the date was parsed from (for user verification). */
    val sourceText: String,
)
