package com.eatbefore.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.domain.usecase.AddBatchUseCase
import com.eatbefore.domain.usecase.BarcodeLookupResult
import com.eatbefore.domain.usecase.LookupProductByBarcodeUseCase
import com.eatbefore.feature.scanner.camera.ScannedCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the scanner resolved the current code to. */
sealed interface ScanResolution {
    val code: String

    data class Found(
        override val code: String,
        val product: Product,
        val fromNetwork: Boolean,
    ) : ScanResolution

    data class NotFound(override val code: String, val type: BarcodeType) : ScanResolution

    data class Error(override val code: String, val type: BarcodeType, val message: String) :
        ScanResolution
}

data class ScannerUiState(
    val isScanning: Boolean = true,
    val isResolving: Boolean = false,
    val resolution: ScanResolution? = null,
    val addedBatchId: Long? = null,
    val torchEnabled: Boolean = false,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val lookupProduct: LookupProductByBarcodeUseCase,
    private val addBatch: AddBatchUseCase,
    private val storageLocationRepository: StorageLocationRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private var lastCode: String? = null
    private var lastHandledAtMillis: Long = 0

    /** Called for every ML Kit detection; debounced against rapid repeats of one code. */
    fun onCodeDetected(scanned: ScannedCode) {
        val current = _state.value
        if (current.isResolving || current.resolution != null) return

        val nowMillis = clock.now().toEpochMilli()
        if (scanned.value == lastCode && nowMillis - lastHandledAtMillis < DEDUP_WINDOW_MS) return
        lastCode = scanned.value
        lastHandledAtMillis = nowMillis

        _state.update { it.copy(isScanning = false, isResolving = true) }
        viewModelScope.launch {
            val result = runCatching { lookupProduct(scanned.value, scanned.type) }
                .getOrElse { BarcodeLookupResult.Error(it.message ?: "lookup failed") }
            val resolution = when (result) {
                is BarcodeLookupResult.Found ->
                    ScanResolution.Found(scanned.value, result.product, result.fromNetwork)

                BarcodeLookupResult.NotFound -> ScanResolution.NotFound(scanned.value, scanned.type)
                is BarcodeLookupResult.Error ->
                    ScanResolution.Error(scanned.value, scanned.type, result.message)
            }
            _state.update { it.copy(isResolving = false, resolution = resolution) }
        }
    }

    /** Handle a code typed manually when the camera can't read it. */
    fun onManualCode(raw: String, type: BarcodeType = BarcodeType.OTHER) {
        onCodeDetected(ScannedCode(raw, type))
    }

    /** Quick-add one package of a resolved product to the default storage location. */
    fun addOnePackage(product: Product) {
        _state.update { it.copy(isResolving = true) }
        viewModelScope.launch {
            val locationId = storageLocationRepository.getDefault()?.id
            if (locationId == null) {
                _state.update { it.copy(isResolving = false) }
                return@launch
            }
            val batchId = addBatch(
                AddBatchUseCase.Params(
                    productId = product.id,
                    storageLocationId = locationId,
                    quantity = 1.0,
                ),
            )
            _state.update { it.copy(isResolving = false, addedBatchId = batchId) }
        }
    }

    /** Dismiss the current result and resume scanning. */
    fun resume() {
        lastCode = null
        _state.update {
            it.copy(isScanning = true, isResolving = false, resolution = null, addedBatchId = null)
        }
    }

    fun toggleTorch() {
        _state.update { it.copy(torchEnabled = !it.torchEnabled) }
    }

    private companion object {
        const val DEDUP_WINDOW_MS = 3_000L
    }
}
