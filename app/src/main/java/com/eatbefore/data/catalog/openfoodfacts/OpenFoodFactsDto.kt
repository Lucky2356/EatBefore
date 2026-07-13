package com.eatbefore.data.catalog.openfoodfacts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal DTO for the Open Food Facts product endpoint. Only the fields the app needs are
 * declared; unknown JSON keys are ignored by the configured [kotlinx.serialization.json.Json].
 * These types live in the data layer and never leak into the domain.
 */
@Serializable
data class OffResponse(
    val status: Int = 0,
    val code: String? = null,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    val categories: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val quantity: String? = null,
)
