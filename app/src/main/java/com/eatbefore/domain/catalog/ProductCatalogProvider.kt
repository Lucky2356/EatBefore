package com.eatbefore.domain.catalog

/**
 * Abstraction over an external product catalog looked up by barcode. Kept as a domain
 * interface so the source (a public API, a bundled dataset, or nothing) can change
 * without touching business logic, and so the domain never depends on network DTOs.
 *
 * Implementations must: time out, tolerate "not found", never block manual entry on
 * failure, and treat all returned data as untrusted (validated before use).
 */
interface ProductCatalogProvider {
    suspend fun lookupByBarcode(barcode: String): CatalogResult
}

/** Outcome of a catalog lookup. Distinguishes "not found" from "error" from "found". */
sealed interface CatalogResult {
    data class Found(val product: CatalogProduct) : CatalogResult
    data object NotFound : CatalogResult
    data class Error(val message: String) : CatalogResult
}

/** Minimal, source-agnostic product info. Not persisted directly; mapped + validated. */
data class CatalogProduct(
    val barcode: String,
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    val imageUrl: String? = null,
    val packageSize: String? = null,
)
