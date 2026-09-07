package com.eatbefore.feature.ocr

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.domain.ocr.DateCandidate
import com.eatbefore.domain.ocr.ExpiryDateOcrProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class OcrUiState(
    val isRecognizing: Boolean = false,
    val hasResult: Boolean = false,
    val rawText: String = "",
    val candidates: List<DateCandidate> = emptyList(),
)

@HiltViewModel
class OcrViewModel @Inject constructor(private val ocrProvider: ExpiryDateOcrProvider, @ApplicationContext private val context: Context) :
    ViewModel() {

    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state.asStateFlow()

    fun recognize(imageUri: String) {
        _state.update { it.copy(isRecognizing = true) }
        viewModelScope.launch {
            val result = ocrProvider.recognize(imageUri)
            // OCR images are transient — remove the cache file once processed (privacy).
            deleteIfCacheFile(imageUri)
            _state.update {
                it.copy(
                    isRecognizing = false,
                    hasResult = true,
                    rawText = result.rawText,
                    candidates = result.candidates,
                )
            }
        }
    }

    fun retry() {
        _state.update { OcrUiState() }
    }

    /**
     * Removes the photo once it has been read, but only the one this app took: OCR images
     * are transient and keeping them would be a privacy leak nobody asked for.
     *
     * The containment check is the whole point. A picture chosen from the gallery is the
     * user's own, kept deliberately, and deleting it would be the worst kind of bug — the
     * silent, unrecoverable kind. The photo picker hands back `content://` URIs whose path
     * matches no file, so today the check would pass by luck; luck is not what should stand
     * between a stray URI and someone's photo library.
     */
    private fun deleteIfCacheFile(imageUri: String) {
        runCatching {
            val path = Uri.parse(imageUri).path ?: return
            val file = File(path).canonicalFile
            val cacheDir = context.cacheDir.canonicalFile
            if (file.startsWith(cacheDir) && file.isFile) file.delete()
        }
    }
}
