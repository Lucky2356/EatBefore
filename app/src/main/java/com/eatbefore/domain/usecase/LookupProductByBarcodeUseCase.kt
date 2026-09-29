package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.common.validation.InputValidator
import com.eatbefore.domain.catalog.CatalogProduct
import com.eatbefore.domain.catalog.CatalogResult
import com.eatbefore.domain.catalog.CatalogSuggestions
import com.eatbefore.domain.catalog.ProductCatalogProvider
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.ProductSource
import com.eatbefore.domain.repository.ProductRepository
import javax.inject.Inject

/** Outcome of resolving a scanned barcode to a product card. */
sealed interface BarcodeLookupResult {
    /** Resolved to a product (either from local cache or freshly cached from the catalog). */
    data class Found(val product: Product, val fromNetwork: Boolean) : BarcodeLookupResult

    /**
     * A catalog knew the code, but its name needs a look before it becomes a card
     * ([CatalogProduct.needsReview]). Nothing is saved; the add form opens pre-filled.
     */
    data class Suggested(val suggestion: CatalogProduct) : BarcodeLookupResult

    /** Neither cache nor catalog knew the code — the user should add it manually. */
    data object NotFound : BarcodeLookupResult

    /** Catalog lookup failed (offline/timeout). Manual entry is still possible. */
    data class Error(val message: String) : BarcodeLookupResult
}

/**
 * Resolves a scanned barcode: local cache first, then the external catalog. A successful
 * catalog hit is persisted (source = SCAN_CACHE) so future scans work offline. External
 * data is treated as untrusted and is fully editable by the user afterwards.
 *
 * A hit that needs review is not persisted but kept in [suggestions], where the add form
 * picks it up — and where a second scan of the same packet finds it without asking the
 * network again.
 */
class LookupProductByBarcodeUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val catalogProvider: ProductCatalogProvider,
    private val suggestions: CatalogSuggestions,
    private val clock: AppClock,
) {

    suspend operator fun invoke(
        barcode: String,
        barcodeType: BarcodeType = BarcodeType.OTHER,
    ): BarcodeLookupResult {
        val clean = InputValidator.sanitizeBarcode(barcode) ?: return BarcodeLookupResult.NotFound

        productRepository.getByBarcode(clean)?.let {
            return BarcodeLookupResult.Found(it, fromNetwork = false)
        }

        suggestions.forBarcode(clean)?.let { return BarcodeLookupResult.Suggested(it) }

        return when (val result = catalogProvider.lookupByBarcode(clean)) {
            is CatalogResult.Found -> if (result.product.needsReview) {
                suggestions.remember(result.product)
                BarcodeLookupResult.Suggested(result.product)
            } else {
                val cached = cacheCatalogProduct(clean, barcodeType, result.product)
                BarcodeLookupResult.Found(cached, fromNetwork = true)
            }

            CatalogResult.NotFound -> BarcodeLookupResult.NotFound
            is CatalogResult.Error -> BarcodeLookupResult.Error(result.message)
        }
    }

    private suspend fun cacheCatalogProduct(
        barcode: String,
        barcodeType: BarcodeType,
        catalog: CatalogProduct,
    ): Product {
        val now = clock.now()
        val product = Product(
            barcode = barcode,
            barcodeType = barcodeType,
            name = InputValidator.requireText(catalog.name, InputValidator.MAX_NAME_LENGTH, "name"),
            brand = InputValidator.sanitizeText(catalog.brand, InputValidator.MAX_BRAND_LENGTH),
            category = InputValidator.sanitizeText(catalog.category, InputValidator.MAX_CATEGORY_LENGTH),
            packageSize = catalog.packageSize,
            imageUri = catalog.imageUrl,
            source = ProductSource.SCAN_CACHE,
            isUserCreated = false,
            createdAt = now,
            updatedAt = now,
        )
        val id = productRepository.upsert(product)
        return product.copy(id = id)
    }
}
