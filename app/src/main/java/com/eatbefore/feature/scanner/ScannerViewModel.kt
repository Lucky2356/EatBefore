package com.eatbefore.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.gs1.Gs1Parser
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
import java.time.LocalDate
import javax.inject.Inject

/**
 * What the scanner resolved the current code to. [expiryFromCode] is present when the
 * scanned code was a GS1 payload (e.g. «Честный знак» DataMatrix) carrying an
 * expiration/best-before date — it pre-fills the batch so the user types nothing.
 */
sealed interface ScanResolution {
    val code: String
    val expiryFromCode: LocalDate?

    data class Found(
        override val code: String,
        val product: Product,
        val fromNetwork: Boolean,
        override val expiryFromCode: LocalDate? = null,
    ) : ScanResolution

    data class NotFound(override val code: String, val type: BarcodeType, override val expiryFromCode: LocalDate? = null) : ScanResolution

    data class Error(
        override val code: String,
        val type: BarcodeType,
        val message: String,
        override val expiryFromCode: LocalDate? = null,
    ) : ScanResolution
}

/**
 * The product code inside a scanned payload.
 *
 * A «Честный знак» DataMatrix is a whole document — GTIN, serial, expiry, crypto tail —
 * and only the GTIN identifies the product. Anything else is passed through unchanged,
 * which is what an ordinary EAN needs. The payload is never executed or fetched.
 */
fun lookupCodeOf(raw: String): String = Gs1Parser.parse(raw)?.normalizedGtin ?: raw

data class ScannerUiState(
    val isScanning: Boolean = true,
    val isResolving: Boolean = false,
    val resolution: ScanResolution? = null,
    val addedBatchId: Long? = null,
    val torchEnabled: Boolean = false,
    /** "Just back from the shop": known codes are added without a dialog each time. */
    val batchMode: Boolean = false,
    /** How many packages the current batch-mode run has added. */
    val batchAddedCount: Int = 0,
    /** Codes scanned in batch mode that the catalog didn't know, kept for the end. */
    val batchUnknownCodes: List<String> = emptyList(),
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

        // «Честный знак» / GS1 DataMatrix and QR: extract the GTIN (product code) and,
        // when encoded, the expiration date. The payload itself is never executed.
        val gs1 = Gs1Parser.parse(scanned.value)
        val lookupCode = lookupCodeOf(scanned.value)
        val expiry = gs1?.expirationDate

        _state.update { it.copy(isScanning = false, isResolving = true) }
        viewModelScope.launch {
            val result = runCatching { lookupProduct(lookupCode, scanned.type) }
                .getOrElse { BarcodeLookupResult.Error(it.message ?: "lookup failed") }
            // Batch mode keeps the camera live: a known product is added straight away and
            // an unknown one is parked, so a full bag of shopping is one continuous scan.
            if (_state.value.batchMode) {
                handleInBatchMode(result, lookupCode, expiry)
                return@launch
            }

            val resolution = when (result) {
                is BarcodeLookupResult.Found ->
                    ScanResolution.Found(lookupCode, result.product, result.fromNetwork, expiry)

                BarcodeLookupResult.NotFound ->
                    ScanResolution.NotFound(lookupCode, scanned.type, expiry)

                is BarcodeLookupResult.Error ->
                    ScanResolution.Error(lookupCode, scanned.type, result.message, expiry)
            }
            _state.update { it.copy(isResolving = false, resolution = resolution) }
        }
    }

    private suspend fun handleInBatchMode(
        result: BarcodeLookupResult,
        code: String,
        expiry: LocalDate?,
    ) {
        when (result) {
            is BarcodeLookupResult.Found -> {
                val locationId = storageLocationRepository.getDefault()?.id
                if (locationId != null) {
                    runCatching {
                        addBatch(
                            AddBatchUseCase.Params(
                                productId = result.product.id,
                                storageLocationId = locationId,
                                quantity = 1.0,
                                expirationDate = expiry,
                            ),
                        )
                    }.onSuccess {
                        _state.update {
                            it.copy(
                                isResolving = false,
                                isScanning = true,
                                batchAddedCount = it.batchAddedCount + 1,
                            )
                        }
                        return
                    }
                }
                _state.update { it.copy(isResolving = false, isScanning = true) }
            }

            // Unknown or unreachable: remember the code, keep scanning, ask at the end.
            else -> _state.update {
                it.copy(
                    isResolving = false,
                    isScanning = true,
                    batchUnknownCodes = (it.batchUnknownCodes + code).distinct(),
                )
            }
        }
    }

    fun setBatchMode(enabled: Boolean) {
        lastCode = null
        _state.update {
            it.copy(
                batchMode = enabled,
                isScanning = true,
                resolution = null,
                batchAddedCount = 0,
                batchUnknownCodes = emptyList(),
            )
        }
    }

    /** Drops one parked code once the user has dealt with it (or chose to skip it). */
    fun consumeUnknownCode(code: String) {
        _state.update { it.copy(batchUnknownCodes = it.batchUnknownCodes - code) }
    }

    /** Handle a code typed manually when the camera can't read it. */
    fun onManualCode(raw: String, type: BarcodeType = BarcodeType.OTHER) {
        onCodeDetected(ScannedCode(raw, type))
    }

    /**
     * Quick-add one package of a resolved product to the default storage location, with
     * the expiration date from the scanned code when it carried one.
     */
    fun addOnePackage(product: Product, expiryFromCode: LocalDate?) {
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
                    expirationDate = expiryFromCode,
                ),
            )
            // Return to live scanning immediately — the result card must not linger and
            // swallow taps while the confirmation snackbar is visible.
            _state.update {
                it.copy(
                    isResolving = false,
                    isScanning = true,
                    resolution = null,
                    addedBatchId = batchId,
                )
            }
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
