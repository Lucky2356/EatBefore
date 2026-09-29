package com.eatbefore.data.catalog.goupc

import kotlinx.serialization.Serializable

/**
 * Minimal DTO for `GET https://go-upc.com/api/v1/code/:code`. Only what the app uses is
 * declared; `upc`, `ean`, `specs` and the rest are skipped by the configured Json.
 */
@Serializable
data class GoUpcResponse(
    val product: GoUpcProduct? = null,
    /**
     * The service guessed missing digits of an incomplete code. The app only sends
     * complete codes, so a guess means the answer is for some other product.
     */
    val inferred: Boolean = false,
)

@Serializable
data class GoUpcProduct(val name: String? = null, val brand: String? = null, val imageUrl: String? = null)
