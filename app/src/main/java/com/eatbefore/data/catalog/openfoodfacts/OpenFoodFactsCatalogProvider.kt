package com.eatbefore.data.catalog.openfoodfacts

import com.eatbefore.core.common.dispatcher.IoDispatcher
import com.eatbefore.core.common.validation.InputValidator
import com.eatbefore.domain.catalog.CatalogProduct
import com.eatbefore.domain.catalog.CatalogResult
import com.eatbefore.domain.catalog.ProductCatalogProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/**
 * [ProductCatalogProvider] backed by the public Open Food Facts API (no API key). Treats
 * responses as untrusted: validates/sanitizes every field, enforces timeouts (via the
 * injected client), and maps failures to [CatalogResult.Error] so the caller can still
 * fall back to manual entry. Runs off the main thread.
 *
 * The barcode is URL-path-encoded to digits-only by [InputValidator]; nothing from the
 * response is executed or interpreted as a command.
 */
class OpenFoodFactsCatalogProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ProductCatalogProvider {

    override suspend fun lookupByBarcode(barcode: String): CatalogResult = withContext(ioDispatcher) {
        val clean = InputValidator.sanitizeBarcode(barcode)
            ?: return@withContext CatalogResult.NotFound
        // Only digits are valid product codes; refuse anything else defensively.
        if (!clean.all { it.isDigit() } || clean.length !in 6..14) {
            return@withContext CatalogResult.NotFound
        }

        val url = "https://world.openfoodfacts.org/api/v2/product/$clean.json" +
            "?fields=code,product_name,brands,categories,image_url,quantity"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                client.newCall(request).execute().use { response ->
                    // The catalog throttles per IP (429) and sheds load under pressure
                    // (503). Both are transient, so back off briefly and try again.
                    if (response.code in RETRYABLE_CODES && attempt < MAX_ATTEMPTS - 1) {
                        return@use
                    }
                    if (!response.isSuccessful) {
                        return@withContext CatalogResult.Error("HTTP ${response.code}")
                    }
                    val body = response.body?.string()
                        ?: return@withContext CatalogResult.Error("Empty response")
                    val parsed = json.decodeFromString<OffResponse>(body)
                    val product = parsed.product
                    if (parsed.status != 1 || product == null || product.productName.isNullOrBlank()) {
                        return@withContext CatalogResult.NotFound
                    }
                    return@withContext CatalogResult.Found(product.toCatalogProduct(clean))
                }
            } catch (e: IOException) {
                if (attempt == MAX_ATTEMPTS - 1) {
                    return@withContext CatalogResult.Error(e.message ?: "Network error")
                }
            } catch (e: Exception) {
                // Malformed JSON or unexpected issues must not crash or block the user.
                return@withContext CatalogResult.Error("Invalid response")
            }
            delay(RETRY_DELAY_MS * (attempt + 1))
        }
        CatalogResult.Error("Catalog unavailable")
    }

    private fun OffProduct.toCatalogProduct(barcode: String) = CatalogProduct(
        barcode = barcode,
        name = InputValidator.requireText(productName, InputValidator.MAX_NAME_LENGTH, "name"),
        brand = InputValidator.sanitizeText(brands?.substringBefore(","), InputValidator.MAX_BRAND_LENGTH),
        category = InputValidator.sanitizeText(categories?.substringBefore(","), InputValidator.MAX_CATEGORY_LENGTH),
        imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
        packageSize = InputValidator.sanitizeText(quantity, 40),
    )

    private companion object {
        // Open Food Facts requires "AppName/Version (contact)" and rate-limits per IP
        // (15 product reads/min), reserving the right to ban unidentified callers.
        val USER_AGENT = "EatBefore/${com.eatbefore.BuildConfig.VERSION_NAME} " +
            "(https://github.com/Lucky2356/EatBefore)"
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 400L
        val RETRYABLE_CODES = setOf(429, 500, 502, 503, 504)
    }
}
