package com.eatbefore.data.catalog.goupc

import com.eatbefore.core.common.dispatcher.IoDispatcher
import com.eatbefore.core.common.validation.InputValidator
import com.eatbefore.core.datastore.CatalogKeyProblem
import com.eatbefore.core.datastore.GoUpcKeyStore
import com.eatbefore.core.diagnostics.DiagnosticsLog
import com.eatbefore.data.catalog.openfoodfacts.OpenFoodFactsCatalogProvider
import com.eatbefore.domain.catalog.CatalogProduct
import com.eatbefore.domain.catalog.CatalogResult
import com.eatbefore.domain.catalog.KeyCheckResult
import com.eatbefore.domain.catalog.KeyedCatalog
import com.eatbefore.domain.catalog.ProductCatalogProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * The second catalog, asked only when Open Food Facts does not know the code. On popular
 * Russian products it finds noticeably more (25 of 30 against 19 in a check on real
 * barcodes), but its names come from shops all over the world — sometimes in Hebrew, with
 * «купить в Алматы» on the end — so every hit is marked [CatalogProduct.needsReview] and
 * goes to the add form rather than straight into the inventory. See ADR-0008.
 *
 * Works only with the user's own key, entered in settings: the free plan is 150 requests
 * a month, and a key shipped in a public repository would be everyone's. Without a key the
 * provider answers [CatalogResult.NotFound] and never touches the network.
 *
 * Sends the barcode and the key, nothing else.
 */
class GoUpcCatalogProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val keyStore: GoUpcKeyStore,
    private val diagnostics: DiagnosticsLog,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ProductCatalogProvider,
    KeyedCatalog {

    override suspend fun lookupByBarcode(barcode: String): CatalogResult = withContext(ioDispatcher) {
        val key = keyStore.key() ?: return@withContext CatalogResult.NotFound
        val clean = InputValidator.sanitizeBarcode(barcode)
            ?.takeIf { code -> code.all { it.isDigit() } && code.length in 8..14 }
            ?: return@withContext CatalogResult.NotFound

        try {
            client.newCall(request(clean, key)).execute().use { response -> read(response, clean) }
        } catch (e: IOException) {
            CatalogResult.Error(e.message ?: "Network error")
        } catch (e: Exception) {
            // The format changed or the body is garbage: say so in the diagnostics, and
            // let the user type the name as if nothing had been found.
            diagnostics.record(TAG, "Could not parse the Go-UPC response", e)
            CatalogResult.Error("Invalid response")
        }
    }

    private suspend fun read(response: Response, barcode: String): CatalogResult = when (response.code) {
        // A refused key and a used-up month both look like «not found» from the scanner.
        // Settings say which it is; the scan itself goes on to manual entry, because
        // retrying will not help.
        HTTP_UNAUTHORIZED -> reportProblem(CatalogKeyProblem.REJECTED)
        HTTP_TOO_MANY -> reportProblem(CatalogKeyProblem.QUOTA)
        HTTP_NOT_FOUND, HTTP_BAD_REQUEST -> {
            keyStore.setProblem(null)
            CatalogResult.NotFound
        }
        HTTP_OK -> {
            keyStore.setProblem(null)
            val body = response.body?.string()
            if (body == null) CatalogResult.Error("Empty response") else parse(body, barcode)
        }
        else -> CatalogResult.Error("HTTP ${response.code}")
    }

    private fun parse(body: String, barcode: String): CatalogResult {
        val parsed = json.decodeFromString<GoUpcResponse>(body)
        val product = parsed.product
        val name = product?.name?.let(::cleanName)
        if (parsed.inferred || product == null || name.isNullOrBlank()) return CatalogResult.NotFound

        return CatalogResult.Found(
            CatalogProduct(
                barcode = barcode,
                name = InputValidator.requireText(name, InputValidator.MAX_NAME_LENGTH, "name"),
                brand = InputValidator.sanitizeText(product.brand?.let(::decodeEntities), InputValidator.MAX_BRAND_LENGTH),
                imageUrl = product.imageUrl?.takeIf { it.startsWith("https://") },
                needsReview = true,
            ),
        )
    }

    private suspend fun reportProblem(problem: CatalogKeyProblem): CatalogResult {
        diagnostics.record(TAG, "Go-UPC refused the lookup: $problem")
        keyStore.setProblem(problem)
        return CatalogResult.NotFound
    }

    /**
     * Asks for one known product. The service checks the code's format before the key, so
     * there is no free way to test a key: this spends one request of the month's quota.
     */
    override suspend fun checkKey(): KeyCheckResult = withContext(ioDispatcher) {
        val key = keyStore.key() ?: return@withContext KeyCheckResult.NOT_SET
        try {
            client.newCall(request(CHECK_CODE, key)).execute().use { response ->
                when (response.code) {
                    HTTP_UNAUTHORIZED -> {
                        keyStore.setProblem(CatalogKeyProblem.REJECTED)
                        KeyCheckResult.REJECTED
                    }
                    HTTP_TOO_MANY -> {
                        keyStore.setProblem(CatalogKeyProblem.QUOTA)
                        KeyCheckResult.QUOTA_USED
                    }
                    // Found or not, the key was accepted.
                    HTTP_OK, HTTP_NOT_FOUND -> {
                        keyStore.setProblem(null)
                        KeyCheckResult.WORKS
                    }
                    else -> KeyCheckResult.UNREACHABLE
                }
            }
        } catch (e: IOException) {
            diagnostics.record(TAG, "Could not check the Go-UPC key", e)
            KeyCheckResult.UNREACHABLE
        }
    }

    private fun request(code: String, key: String): Request = Request.Builder()
        .url("$BASE_URL$code")
        // The documented alternative is ?key= in the URL, which ends up in every proxy log.
        .header("Authorization", "Bearer $key")
        .header("User-Agent", OpenFoodFactsCatalogProvider.USER_AGENT)
        .build()

    internal companion object {
        const val BASE_URL = "https://go-upc.com/api/v1/code/"
        private const val TAG = "CATALOG"

        /** The product from Go-UPC's own documentation; any real code would do. */
        const val CHECK_CODE = "781138811156"

        private const val HTTP_OK = 200
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY = 429

        private val SHOP_TAIL = Regex("""\s+купить(\s.*)?$""", RegexOption.IGNORE_CASE)
        private val SPACES = Regex("""\s+""")
        private val ENTITIES = mapOf(
            "&quot;" to "\"",
            "&amp;" to "&",
            "&#39;" to "'",
            "&apos;" to "'",
            "&laquo;" to "«",
            "&raquo;" to "»",
            "&nbsp;" to " ",
        )

        private fun decodeEntities(text: String): String =
            ENTITIES.entries.fold(text) { acc, (entity, char) -> acc.replace(entity, char) }

        /**
         * Tidies a name taken from a shop's page: HTML entities, doubled spaces and the
         * «Купить в Алматы» a listing title often ends with. Anything subtler — a name in
         * another language — is left for the user to see and fix on the form.
         */
        fun cleanName(raw: String): String =
            decodeEntities(raw)
                .replace(SPACES, " ")
                .trim()
                .replace(SHOP_TAIL, "")
                .trim()
    }
}
