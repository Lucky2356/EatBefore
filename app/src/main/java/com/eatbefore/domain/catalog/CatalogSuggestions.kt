package com.eatbefore.domain.catalog

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catalog hits waiting for the user's look before they become cards — see
 * [CatalogProduct.needsReview].
 *
 * The scanner finds one, the add form opens with its name filled in, and it stays here
 * until then. That keeps the hand-over out of the navigation route, and a code parked in
 * batch mode still arrives at the form pre-filled. It also means scanning the same packet
 * twice spends one request, not two — which matters when the source allows 150 a month.
 *
 * Kept in memory only: a suggestion is a hint for the next minute, not data.
 */
@Singleton
class CatalogSuggestions @Inject constructor() {

    private val byBarcode = object : LinkedHashMap<String, CatalogProduct>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CatalogProduct>?): Boolean =
            size > MAX_KEPT
    }

    @Synchronized
    fun remember(product: CatalogProduct) {
        byBarcode[product.barcode] = product
    }

    @Synchronized
    fun forBarcode(barcode: String): CatalogProduct? = byBarcode[barcode]

    /** Once the card exists it answers for the code, and the suggestion only goes stale. */
    @Synchronized
    fun forget(barcode: String) {
        byBarcode.remove(barcode)
    }

    private companion object {
        /** A long shopping trip's worth; beyond it the oldest is dropped. */
        const val MAX_KEPT = 100
    }
}
