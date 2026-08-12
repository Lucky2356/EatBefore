package com.eatbefore.data.catalog.openfoodfacts

import com.eatbefore.core.common.dispatcher.IoDispatcher
import com.eatbefore.core.common.validation.InputValidator
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.core.diagnostics.DiagnosticsLog
import com.eatbefore.domain.catalog.CatalogContributor
import com.eatbefore.domain.catalog.CatalogProduct
import com.eatbefore.domain.catalog.ContributionResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/**
 * Publishes a hand-entered product to Open Food Facts so the barcode resolves for
 * everyone next time. This is the only lever that actually grows Russian coverage: no
 * free Russian product API exists to read from, so the shared database has to be fed.
 *
 * Open Food Facts rejects anonymous writes, so this requires the user's own account,
 * configured in settings. Shipping a shared app account instead would attribute every
 * edit to one identity and would be treated as abuse.
 *
 * Sends only barcode, name, brand and quantity. Never stock, expiry dates, locations or
 * history — those stay on the device.
 */
class OpenFoodFactsContributor @Inject constructor(
    private val client: OkHttpClient,
    private val preferences: UserPreferencesRepository,
    private val diagnostics: DiagnosticsLog,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CatalogContributor {

    override suspend fun isConfigured(): Boolean =
        preferences.preferences.first().offUsername != null && preferences.offPassword() != null

    /**
     * Sends the credentials with a deliberately empty edit. The catalog validates the
     * login before it looks at what is being changed, so an edit carrying no fields is
     * enough to learn whether the account works — and it changes nothing if it does.
     */
    override suspend fun checkAccount(): ContributionResult = withContext(ioDispatcher) {
        val username = preferences.preferences.first().offUsername
            ?: return@withContext ContributionResult.NotConfigured
        val password = preferences.offPassword()
            ?: return@withContext ContributionResult.NotConfigured

        val form = FormBody.Builder()
            .add("user_id", username)
            .add("password", password)
            .add("app_name", APP_NAME)
            .add("app_version", com.eatbefore.BuildConfig.VERSION_NAME)
            .build()

        val request = Request.Builder()
            .url(WRITE_URL)
            .header("User-Agent", OpenFoodFactsCatalogProvider.USER_AGENT)
            .post(form)
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) {
                    return@use ContributionResult.AuthFailed
                }
                val body = response.body?.string().orEmpty()
                when {
                    body.isAuthFailure() -> ContributionResult.AuthFailed
                    // Anything else means the login was accepted and the catalog moved on
                    // to complaining about the missing barcode, which is what we sent.
                    response.isSuccessful -> ContributionResult.Success
                    else -> ContributionResult.Failed("HTTP ${response.code}")
                }
            }
        }.getOrElse { error ->
            diagnostics.record("CATALOG", "Could not check the account", error)
            ContributionResult.Failed(error.message ?: "Нет соединения")
        }
    }

    override suspend fun contribute(product: CatalogProduct): ContributionResult = withContext(ioDispatcher) {
        val username = preferences.preferences.first().offUsername
            ?: return@withContext ContributionResult.NotConfigured
        val password = preferences.offPassword()
            ?: return@withContext ContributionResult.NotConfigured

        val barcode = InputValidator.sanitizeBarcode(product.barcode)
            ?: return@withContext ContributionResult.Failed("Некорректный штрихкод")
        val name = product.name.trim()
        if (name.isEmpty()) return@withContext ContributionResult.Failed("Пустое название")

        val form = FormBody.Builder()
            .add("code", barcode)
            .add("user_id", username)
            .add("password", password)
            .add("product_name", name)
            // Identifies the client to the catalog's moderation tooling, as OFF asks.
            .add("app_name", APP_NAME)
            .add("app_version", com.eatbefore.BuildConfig.VERSION_NAME)
            .apply {
                product.brand?.takeIf { it.isNotBlank() }?.let { add("brands", it.trim()) }
                product.packageSize?.takeIf { it.isNotBlank() }?.let { add("quantity", it.trim()) }
            }
            .build()

        val request = Request.Builder()
            .url(WRITE_URL)
            .header("User-Agent", OpenFoodFactsCatalogProvider.USER_AGENT)
            .post(form)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) {
                    return@withContext ContributionResult.AuthFailed
                }
                if (!response.isSuccessful) {
                    return@withContext ContributionResult.Failed("HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                // The endpoint answers 200 even for a rejected edit; the JSON status is
                // what actually says whether the product was saved.
                return@withContext when {
                    body.isSaved() -> ContributionResult.Success
                    body.isAuthFailure() -> ContributionResult.AuthFailed
                    else -> ContributionResult.Failed(body.verboseStatus() ?: "Каталог отклонил запись")
                }
            }
        } catch (e: IOException) {
            return@withContext ContributionResult.Failed(e.message ?: "Нет соединения")
        } catch (e: Exception) {
            // "Ошибка отправки" tells the user nothing they can act on and tells us less.
            diagnostics.record("CATALOG", "Sending the product failed", e)
            return@withContext ContributionResult.Failed("Ошибка отправки")
        }
    }

    internal companion object {
        const val WRITE_URL = "https://world.openfoodfacts.org/cgi/product_jqm2.pl"
        const val APP_NAME = "EatBefore"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403

        private val STATUS = Regex("\"status\"\\s*:\\s*(\\d+)")
        private val STATUS_VERBOSE = Regex("\"status_verbose\"\\s*:\\s*\"([^\"]*)\"")

        /**
         * Phrases the catalog uses when it will not accept the login. Checked against the
         * real service rather than guessed: an unauthenticated write answers
         * `{"status_verbose":"no user credentials","status":0}`. The previous code looked
         * for "user name or password", which this endpoint never says — so a genuinely
         * rejected login was reported to the user as a rejected *product*.
         */
        private val AUTH_FAILURE_PHRASES = listOf(
            "no user credentials",
            "user name or password",
            "incorrect user",
            "not authorized",
        )

        /** The catalog saved the edit. Anything else is a refusal of some kind. */
        fun String.isSaved(): Boolean = STATUS.find(this)?.groupValues?.get(1) == "1"

        fun String.isAuthFailure(): Boolean {
            val verbose = verboseStatus() ?: this
            return AUTH_FAILURE_PHRASES.any { verbose.contains(it, ignoreCase = true) }
        }

        /** The catalog's own explanation, which is more use than anything we could invent. */
        fun String.verboseStatus(): String? =
            STATUS_VERBOSE.find(this)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }
}
