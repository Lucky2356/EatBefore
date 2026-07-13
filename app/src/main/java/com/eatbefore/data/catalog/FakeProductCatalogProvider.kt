package com.eatbefore.data.catalog

import com.eatbefore.domain.catalog.CatalogResult
import com.eatbefore.domain.catalog.ProductCatalogProvider
import javax.inject.Inject

/**
 * Placeholder catalog used until the real barcode-lookup source is wired in the scanner
 * milestone. Always reports "not found" so manual entry is never blocked. Exists so the
 * rest of the app can depend on [ProductCatalogProvider] today.
 */
class FakeProductCatalogProvider @Inject constructor() : ProductCatalogProvider {
    override suspend fun lookupByBarcode(barcode: String): CatalogResult = CatalogResult.NotFound
}
