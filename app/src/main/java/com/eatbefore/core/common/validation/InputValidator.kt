package com.eatbefore.core.common.validation

/**
 * Central input limits and sanitation. All externally-sourced text (manual entry,
 * scanned QR payloads, imported backups, external catalog responses) must pass through
 * here before persistence — defends against oversized values and control characters
 * (see THREAT_MODEL.md).
 */
object InputValidator {
    const val MAX_NAME_LENGTH = 120
    const val MAX_BRAND_LENGTH = 120
    const val MAX_CATEGORY_LENGTH = 60
    const val MAX_DESCRIPTION_LENGTH = 1_000
    const val MAX_NOTE_LENGTH = 500
    const val MAX_BARCODE_LENGTH = 128
    const val MAX_QUANTITY = 1_000_000.0

    /** ISO 4217 codes are 3 chars; a little slack for informal entries like "руб.". */
    const val MAX_CURRENCY_LENGTH = 8

    /** Trims, strips control characters, and clamps length. Returns null if blank. */
    fun sanitizeText(input: String?, maxLength: Int): String? {
        if (input == null) return null
        val cleaned = input
            .filterNot { it.isISOControl() && it != '\n' }
            .trim()
            .take(maxLength)
        return cleaned.ifBlank { null }
    }

    /** Sanitizes a required text field, throwing if it ends up blank. */
    fun requireText(input: String?, maxLength: Int, field: String): String =
        sanitizeText(input, maxLength)
            ?: throw IllegalArgumentException("Field '$field' must not be blank")

    /** Clamps a quantity to the valid non-negative range. */
    fun clampQuantity(value: Double): Double = value.coerceIn(0.0, MAX_QUANTITY)

    /** A barcode is treated purely as opaque data — never executed or interpreted. */
    fun sanitizeBarcode(input: String?): String? =
        sanitizeText(input, MAX_BARCODE_LENGTH)?.filterNot { it.isWhitespace() }
}
