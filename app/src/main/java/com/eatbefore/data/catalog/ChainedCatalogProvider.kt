package com.eatbefore.data.catalog

import com.eatbefore.domain.catalog.CatalogResult
import com.eatbefore.domain.catalog.ProductCatalogProvider

/**
 * Queries several catalogs in order and returns the first real hit.
 *
 * Ordering matters: cheap/offline sources go first so a lookup that can be answered
 * locally never touches the network. A source that answers [CatalogResult.NotFound] or
 * fails is not fatal — the next one is tried, and only if every source came up empty is
 * the combined result reported.
 *
 * An [CatalogResult.Error] anywhere is remembered but does not stop the chain: a product
 * missing from a broken source may still exist in the next one. The error is reported
 * only if nothing was found at all, so the UI can distinguish "no such product" (offer
 * manual entry immediately) from "we could not reach the catalogs" (offer a retry).
 */
class ChainedCatalogProvider(private val providers: List<ProductCatalogProvider>) : ProductCatalogProvider {

    override suspend fun lookupByBarcode(barcode: String): CatalogResult {
        var lastError: CatalogResult.Error? = null

        for (provider in providers) {
            when (val result = provider.lookupByBarcode(barcode)) {
                is CatalogResult.Found -> return result
                is CatalogResult.Error -> lastError = result
                CatalogResult.NotFound -> Unit
            }
        }

        // Every source was reachable and none knew the code — that is a genuine miss.
        return lastError ?: CatalogResult.NotFound
    }
}
