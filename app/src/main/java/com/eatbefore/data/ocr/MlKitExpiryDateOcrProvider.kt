package com.eatbefore.data.ocr

import android.content.Context
import android.net.Uri
import com.eatbefore.core.common.dispatcher.IoDispatcher
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.ocr.ExpiryDateOcrProvider
import com.eatbefore.domain.ocr.ExpiryDateParser
import com.eatbefore.domain.ocr.OcrResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * On-device OCR of expiration dates using ML Kit text recognition (Latin script — digits
 * and separators are read reliably). Recognized text is handed to [ExpiryDateParser];
 * nothing is auto-applied — the UI confirms candidates.
 *
 * Untrusted input hardening: oversized images are rejected before decoding, and any
 * failure degrades to an empty result so the user simply falls back to manual entry.
 */
class MlKitExpiryDateOcrProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: ExpiryDateParser,
    private val clock: AppClock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ExpiryDateOcrProvider {

    override suspend fun recognize(imageUri: String): OcrResult = withContext(ioDispatcher) {
        val uri = runCatching { Uri.parse(imageUri) }.getOrNull()
            ?: return@withContext EMPTY
        if (!isSizeAcceptable(uri)) return@withContext EMPTY

        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val text = recognizer.use { it.processAwait(image) }
            OcrResult(rawText = text, candidates = parser.parse(text, clock.today()))
        } catch (e: Exception) {
            // Corrupt image, unreadable URI, or recognizer failure — never crash.
            EMPTY
        }
    }

    /** Guards against decoding excessively large images (memory-exhaustion defense). */
    private fun isSizeAcceptable(uri: Uri): Boolean {
        val size = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: return true // Unknown size (e.g. our own cache file) — allow.
        return size in 0..MAX_IMAGE_BYTES
    }

    private suspend fun com.google.mlkit.vision.text.TextRecognizer.processAwait(
        image: InputImage,
    ): String = suspendCoroutine { continuation ->
        process(image)
            .addOnSuccessListener { continuation.resume(it.text) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
        val EMPTY = OcrResult(rawText = "", candidates = emptyList())
    }
}
