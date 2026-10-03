package com.eatbefore.feature.addmanual

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.R
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.designsystem.format.defaultCurrencyCode
import com.eatbefore.domain.catalog.CatalogContributor
import com.eatbefore.domain.catalog.CatalogProduct
import com.eatbefore.domain.catalog.CatalogSuggestions
import com.eatbefore.domain.catalog.ContributionResult
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.HomemadeKind
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType
import com.eatbefore.domain.repository.ProductRepository
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.domain.shelflife.HomemadeShelfLife
import com.eatbefore.domain.shelflife.TypicalShelfLife
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
    /**
     * Free text, like the same field on the product card. Optional, but worth asking for
     * here: set at the moment of adding it also sharpens the opening shelf-life guess,
     * which [AddManualProductUseCase] derives from name *and* category.
     */
    val category: String = "",
    /**
     * Categories already in use, offered as chips. Typing a category by hand every time
     * is how one household ends up with «молочка», «Молочное» and «молоко» as three
     * different things; tapping the one that already exists is what keeps them one.
     */
    val knownCategories: List<String> = emptyList(),
    /**
     * Products already in the catalogue whose name matches what is being typed. Picking one
     * fills in its brand, category and unit, and — because the name then matches exactly —
     * the new package lands on the existing card instead of starting a second one.
     */
    val nameSuggestions: List<NameSuggestion> = emptyList(),
    /**
     * Set when the form is for home cooking rather than shopping. The date asked for then
     * is when it was made, the shelf-life hint comes from the home-made table, and barcode,
     * brand and the catalogue offer go away — none of them mean anything for a pot of soup.
     */
    val homemadeKind: HomemadeKind? = null,
    /** When it was made. Only used with [homemadeKind]; a jar can be from last summer. */
    val cookedDate: LocalDate? = null,
    /** Empty unless scanned or typed; a product with one can be offered to the catalog. */
    val barcode: String = "",
    /**
     * The name a catalog guessed for the scanned code, pre-filled into [name]. Kept to
     * say where it came from for as long as the field still holds it untouched.
     */
    val nameFromCatalog: String? = null,
    val quantity: String = "1",
    val unit: MeasurementUnit = MeasurementUnit.PIECE,
    val locations: List<StorageLocation> = emptyList(),
    val selectedLocationId: Long? = null,
    val expirationDate: LocalDate? = null,
    /**
     * Typical days this kind of food keeps, when the name matches something known. Offered
     * as one more expiry chip; null means no suggestion rather than a made-up one.
     */
    val suggestedShelfLifeDays: Int? = null,
    val note: String = "",
    /**
     * What it cost. Optional and asked for last: it is the one field that pays for itself
     * only later, when the analytics screen can say how much went into the bin.
     */
    val price: String = "",
    val isSaving: Boolean = false,
    val nameError: Boolean = false,
    /**
     * Zero typed in the amount. The use case refuses an empty package, and the refusal used
     * to escape as a crash of the whole app; now the field says what is wrong.
     */
    val quantityError: Boolean = false,
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

/** A known product offered while its name is typed. */
data class NameSuggestion(val productId: Long, val name: String, val brand: String?)

/** The product about to be offered to the shared catalog. */
data class ContributeOffer(val name: String, val barcode: String)

@HiltViewModel
class AddManualViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addManualProduct: AddManualProductUseCase,
    private val catalogContributor: CatalogContributor,
    private val storageLocationRepository: StorageLocationRepository,
    private val productRepository: ProductRepository,
    private val catalogSuggestions: CatalogSuggestions,
    private val clock: AppClock,
) : ViewModel() {

    /**
     * The barcode. Pre-filled when arriving from the scanner with a code the catalog did
     * not know, and typed by hand otherwise — until it was editable, a product added
     * without the scanner could never be offered to the catalog at all, no matter how
     * correctly the account was set up.
     */
    private val scannedBarcode: String? = savedStateHandle[Routes.ADD_MANUAL_ARG_BARCODE]

    /** Expiration extracted from a scanned GS1 code (Честный знак), if any. */
    private val expiryFromCode: LocalDate? =
        savedStateHandle.get<Long>(Routes.ADD_MANUAL_ARG_EXPIRY)
            ?.takeIf { it >= 0 }
            ?.let(LocalDate::ofEpochDay)

    private val initialHomemadeKind: HomemadeKind? =
        savedStateHandle.get<String>(Routes.ADD_MANUAL_ARG_HOMEMADE)
            ?.let { name -> HomemadeKind.entries.firstOrNull { it.name == name } }

    /** What a catalog guessed for the scanned code, waiting to be checked here. */
    private val suggestion = scannedBarcode?.let(catalogSuggestions::forBarcode)

    private val _state = MutableStateFlow(
        AddManualUiState(
            name = suggestion?.name.orEmpty(),
            brand = suggestion?.brand.orEmpty(),
            nameFromCatalog = suggestion?.name,
            expirationDate = expiryFromCode,
            barcode = scannedBarcode.orEmpty(),
            homemadeKind = initialHomemadeKind,
            cookedDate = initialHomemadeKind?.let { clock.today() },
        ),
    )
    val state: StateFlow<AddManualUiState> = _state.asStateFlow()

    /**
     * The catalogue as last seen, for name suggestions. Above `init` on purpose: the
     * collector there runs inside the constructor on a phone, and an initializer below it
     * would then reset the list it had just filled.
     */
    private var knownProducts: List<Product> = emptyList()

    init {
        // A home-made form has a hint before anything is typed: soup is three days in the
        // fridge whatever it is called.
        _state.update { it.withSuggestion(before = it) }
        viewModelScope.launch {
            // Keep the picker in sync with locations; default to the primary location.
            storageLocationRepository.observeActive().collect { list ->
                _state.update { current ->
                    current.copy(
                        locations = list,
                        selectedLocationId = current.selectedLocationId
                            ?: list.firstOrNull { it.isDefault }?.id
                            ?: list.firstOrNull()?.id,
                    ).withSuggestion(before = current)
                }
            }
        }
        viewModelScope.launch {
            // Cards in use only: a category that survives solely on a struck-off card is
            // not one the household still sorts by, and offering it invites it back.
            productRepository.observeActive().collect { products ->
                knownProducts = products
                val categories = products
                    .mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }
                    .distinctBy { it.lowercase() }
                    .sorted()
                _state.update { it.copy(knownCategories = categories) }
            }
        }
    }

    // Recomputed as the name is typed: the suggestion is only useful while the expiry is
    // still being chosen, and by then the name is what identifies the product — the
    // category is rarely filled in by hand.
    fun onName(value: String) = _state.update {
        it.copy(name = value, nameError = false, nameSuggestions = suggestionsFor(value, it.homemadeKind))
            .withSuggestion(before = it)
    }

    /** Fills the form from a known product picked among the suggestions. */
    fun onNameSuggestion(productId: Long) {
        val product = knownProducts.firstOrNull { it.id == productId } ?: return
        _state.update { current ->
            current.copy(
                name = product.name,
                brand = product.brand.orEmpty().takeIf { current.homemadeKind == null } ?: current.brand,
                category = product.category ?: current.category,
                unit = product.measurementUnit,
                barcode = current.barcode.ifBlank { product.barcode.orEmpty() },
                nameError = false,
                nameSuggestions = emptyList(),
            ).withSuggestion(before = current)
        }
    }

    /**
     * Known products whose name contains [query], those starting with it first. Home
     * cooking is matched among home cooking and bought among bought — the same split the
     * duplicate check makes, so a suggestion never lands the package on the wrong card.
     */
    private fun suggestionsFor(query: String, homemadeKind: HomemadeKind?): List<NameSuggestion> {
        val needle = query.trim()
        if (needle.length < MIN_SUGGESTION_QUERY) return emptyList()
        return knownProducts
            .filter { (it.homemadeKind != null) == (homemadeKind != null) }
            .filter { it.name.contains(needle, ignoreCase = true) && !it.name.equals(needle, ignoreCase = true) }
            .sortedWith(compareBy({ !it.name.startsWith(needle, ignoreCase = true) }, { it.name.length }))
            .take(MAX_SUGGESTIONS)
            .map { NameSuggestion(productId = it.id, name = it.name, brand = it.brand) }
    }

    /** Dish or preserve. The two keep for days and for months, so the hint follows. */
    fun onHomemadeKind(kind: HomemadeKind) = _state.update { it.copy(homemadeKind = kind).withSuggestion(before = it) }

    fun onCookedDate(date: LocalDate) = _state.update { it.copy(cookedDate = date).withSuggestion(before = it) }

    /**
     * The shelf-life hint for what is on the form. Home cooking always gets one — cooked
     * food in the fridge is three days whatever it is called — and a dish headed for the
     * freezer gets the freezer's figure.
     */
    private fun AddManualUiState.withSuggestion(before: AddManualUiState): AddManualUiState {
        val kind = homemadeKind
        val days = if (kind == null) {
            TypicalShelfLife.suggestDays(name)
        } else {
            val frozen = locations.firstOrNull { it.id == selectedLocationId }?.type == StorageType.FREEZER
            HomemadeShelfLife.suggestDays(name, kind, frozen)
        }
        val updated = copy(suggestedShelfLifeDays = days)
        // A date the user took from the hint follows the hint. Otherwise picking «обычно
        // 365 дн.» and only then setting the jar's date to last summer would keep a year
        // from today — the chip goes grey, but the date under it stays quietly wrong.
        // And a hint that disappears (the name was cleared) takes nothing with it.
        val tookHint = expirationDate != null && expirationDate == before.suggestedExpiry()
        val followed = updated.suggestedExpiry()
        return if (tookHint && followed != null) updated.copy(expirationDate = followed) else updated
    }

    /** The date the typical-figure chip stands for, counted as the chip counts it. */
    private fun AddManualUiState.suggestedExpiry(): LocalDate? =
        suggestedShelfLifeDays?.let { days -> (cookedDate ?: clock.today()).plusDays(days.toLong()) }
    fun onBrand(value: String) = _state.update { it.copy(brand = value) }
    fun onCategory(value: String) = _state.update { it.copy(category = value) }

    // Barcodes are digits and, for Честный знак, a few symbols; whitespace never belongs.
    fun onBarcode(value: String) =
        _state.update { it.copy(barcode = value.take(MAX_BARCODE_LENGTH).filterNot { c -> c.isWhitespace() }) }
    fun onQuantity(value: String) =
        _state.update { it.copy(quantity = value.filter { c -> c.isDigit() || c == '.' }, quantityError = false) }

    /**
     * Steps the amount by one, never below one — zero packages of something is not a thing
     * you add to an inventory, and the field stays open for anything unusual.
     */
    fun stepQuantity(delta: Int) = _state.update {
        val current = it.quantity.toDoubleOrNull() ?: 1.0
        val stepped = (current + delta).coerceAtLeast(1.0)
        it.copy(quantity = formatAmount(stepped), quantityError = false)
    }

    fun onUnit(unit: MeasurementUnit) = _state.update { it.copy(unit = unit) }
    fun onLocation(id: Long) = _state.update { it.copy(selectedLocationId = id).withSuggestion(before = it) }
    fun onNote(value: String) = _state.update { it.copy(note = value) }

    // Comma is what a Russian keyboard offers for a decimal; accept it as a full stop
    // rather than silently dropping the kopecks the user typed.
    fun onPrice(value: String) = _state.update {
        it.copy(price = value.replace(',', '.').filter { c -> c.isDigit() || c == '.' })
    }

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
        // Blank or unreadable still means one package, as it always has; only a number
        // that is not more than zero is refused.
        val quantity = current.quantity.toDoubleOrNull() ?: 1.0
        if (quantity <= 0.0) {
            _state.update { it.copy(quantityError = true) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val homemade = current.homemadeKind != null
            // Hidden on the home-made form, so anything left in them is not the user's.
            val barcode = current.barcode.trim().ifBlank { null }?.takeUnless { homemade }
            val id = addManualProduct(
                AddManualProductUseCase.Params(
                    name = current.name,
                    brand = current.brand.ifBlank { null }?.takeUnless { homemade },
                    category = current.category.ifBlank { null },
                    barcode = barcode,
                    barcodeType = if (barcode != null) BarcodeType.OTHER else BarcodeType.NONE,
                    storageLocationId = locationId,
                    quantity = quantity,
                    measurementUnit = current.unit,
                    expirationDate = current.expirationDate,
                    note = current.note.ifBlank { null },
                    price = current.price.toDoubleOrNull()?.takeIf { it > 0 },
                    currency = current.price.toDoubleOrNull()?.let { defaultCurrencyCode() },
                    homemadeKind = current.homemadeKind,
                    purchaseDate = current.cookedDate?.takeIf { homemade },
                ),
            )
            // The card now answers for the code; the guess would only go stale.
            barcode?.let(catalogSuggestions::forget)
            // Offer to publish any product that carries a barcode, however it got there,
            // and only when an account is actually usable — otherwise say nothing.
            val offer = barcode?.let { code ->
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

    /** Keeps whole amounts free of a trailing ".0" in the text field. */
    private fun formatAmount(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    /** Today per the app clock, for the expiry presets. */
    val today: LocalDate get() = clock.today()

    private companion object {
        /** Long enough for a Честный знак payload, short enough to stay a barcode. */
        const val MAX_BARCODE_LENGTH = 128
        const val MIN_SUGGESTION_QUERY = 2
        const val MAX_SUGGESTIONS = 5
    }
}
