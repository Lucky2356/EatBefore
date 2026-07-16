package com.eatbefore.feature.ocr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.domain.ocr.DateCandidate
import com.eatbefore.domain.ocr.ExpiryDateOcrProvider
import dagger.hilt.android.lifecycle.HiltViewModel
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
class OcrViewModel @Inject constructor(private val ocrProvider: ExpiryDateOcrProvider) : ViewModel() {

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

    private fun deleteIfCacheFile(imageUri: String) {
        runCatching {
            val path = android.net.Uri.parse(imageUri).path ?: return
            val file = File(path)
            if (file.exists()) file.delete()
        }
    }
}
