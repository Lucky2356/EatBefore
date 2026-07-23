package com.eatbefore.feature.addmanual

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.R
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.catalog.CatalogContributor
import com.eatbefore.domain.catalog.CatalogProduct
import com.eatbefore.domain.catalog.ContributionResult
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.domain.usecase.AddManualProductUseCase
import com.eatbefore.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AddManualUiState(
    val name: String = "",
    val brand: String = "",
    val quantity: String = "1",
    val unit: MeasurementUnit = MeasurementUnit.PIECE,
    val locations: List<StorageLocation> = emptyList(),
    val selectedLocationId: Long? = null,
    val expirationDate: LocalDate? = null,
    val note: String = "",
    val isSaving: Boolean = false,
    val nameError: Boolean = false,
    val savedBatchId: Long? = null,
    /**
     * Set after saving a product that carried a barcode the open catalog does not know.
     * While this is non-null the screen stays put so the user can decide; navigation
     * happens once it is resolved.
     */
    val contributeOffer: ContributeOffer? = null,
    val isContributing: Boolean = false,
    /** One-shot message (string resource) about the contribution outcome. */
    val message: Int? = null,
)

/** The product about to be offered to the shared catalog. */
data class ContributeOffer(val name: String, val barcode: String)

@HiltViewModel
class AddManualViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addManualProduct: AddManualProductUseCase,
    private val catalogContributor: CatalogContributor,
    private val storageLocationRepository: StorageLocationRepository,
    private val clock: AppClock,
) : ViewModel() {

    /** Optional barcode passed when arriving from the scanner (unknown product). */
    private val barcode: String? = savedStateHandle[Routes.ADD_MANUAL_ARG_BARCODE]

    /** Expiration extracted from a scanned GS1 code (Честный знак), if any. */
    private val expiryFromCode: LocalDate? =
        savedStateHandle.get<Long>(Routes.ADD_MANUAL_ARG_EXPIRY)
            ?.takeIf { it >= 0 }
            ?.let(LocalDate::ofEpochDay)

    private val _state = MutableStateFlow(AddManualUiState(expirationDate = expiryFromCode))
    val state: StateFlow<AddManualUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Keep the picker in sync with locations; default to the primary location.
            storageLocationRepository.observeActive().collect { list ->
                _state.update { current ->
                    current.copy(
                        locations = list,
                        selectedLocationId = current.selectedLocationId
                            ?: list.firstOrNull { it.isDefault }?.id
                            ?: list.firstOrNull()?.id,
                    )
                }
            }
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value, nameError = false) }
    fun onBrand(value: String) = _state.update { it.copy(brand = value) }
    fun onQuantity(value: String) = _state.update { it.copy(quantity = value.filter { c -> c.isDigit() || c == '.' }) }
    fun onUnit(unit: MeasurementUnit) = _state.update { it.copy(unit = unit) }
    fun onLocation(id: Long) = _state.update { it.copy(selectedLocationId = id) }
    fun onNote(value: String) = _state.update { it.copy(note = value) }
    fun onExpirationDate(date: LocalDate?) = _state.update { it.copy(expirationDate = date) }

    /** Quick expiry presets relative to today. Null clears the date. */
    fun onQuickExpiry(daysFromToday: Long?) = _state.update {
        it.copy(expirationDate = daysFromToday?.let { d -> clock.today().plusDays(d) })
    }

    fun save() {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        val locationId = current.selectedLocationId ?: return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val quantity = current.quantity.toDoubleOrNull() ?: 1.0
            val id = addManualProduct(
                AddManualProductUseCase.Params(
                    name = current.name,
                    brand = current.brand.ifBlank { null },
                    barcode = barcode,
                    barcodeType = if (barcode != null) BarcodeType.OTHER else BarcodeType.NONE,
                    storageLocationId = locationId,
                    quantity = quantity,
                    measurementUnit = current.unit,
                    expirationDate = current.expirationDate,
                    note = current.note.ifBlank { null },
                ),
            )
            // Offer to publish only for a barcode the catalog missed, and only when the
            // user has linked an account — otherwise just hint that it is possible.
            val offer = barcode
                ?.takeIf { it.isNotBlank() }
                ?.let { code ->
                    if (catalogContributor.isConfigured()) {
                        ContributeOffer(name = current.name.trim(), barcode = code)
                    } else {
                        null
                    }
                }

            _state.update {
                it.copy(
                    isSaving = false,
                    savedBatchId = id,
                    contributeOffer = offer,
                )
            }
        }
    }

    /** Publishes the just-saved product to the shared catalog after explicit confirmation. */
    fun confirmContribution() {
        val offer = _state.value.contributeOffer ?: return
        _state.update { it.copy(isContributing = true) }
        viewModelScope.launch {
            val result = catalogContributor.contribute(
                CatalogProduct(
                    barcode = offer.barcode,
                    name = offer.name,
                    brand = _state.value.brand.ifBlank { null },
                    packageSize = null,
                ),
            )
            _state.update {
                it.copy(
                    isContributing = false,
                    contributeOffer = null,
                    message = when (result) {
                        ContributionResult.Success -> R.string.contribute_success
                        ContributionResult.AuthFailed -> R.string.contribute_auth_failed
                        ContributionResult.NotConfigured -> R.string.contribute_setup
                        is ContributionResult.Failed -> R.string.contribute_failed
                    },
                )
            }
        }
    }

    fun declineContribution() = _state.update { it.copy(contributeOffer = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
