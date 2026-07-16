package com.eatbefore.data.ocr

import com.eatbefore.domain.ocr.ExpiryDateOcrProvider
import com.eatbefore.domain.ocr.OcrResult
import javax.inject.Inject

/**
 * Placeholder OCR provider used until ML Kit text recognition is wired in the OCR
 * milestone. Returns no candidates so callers fall back to manual date entry.
 */
class NoopExpiryDateOcrProvider @Inject constructor() : ExpiryDateOcrProvider {
    override suspend fun recognize(imageUri: String): OcrResult = OcrResult(rawText = "", candidates = emptyList())
}
