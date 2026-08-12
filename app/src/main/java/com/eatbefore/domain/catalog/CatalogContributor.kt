package com.eatbefore.domain.catalog

/** Outcome of publishing a product to a shared open catalog. */
sealed interface ContributionResult {
    data object Success : ContributionResult

    /** No account configured — the caller should point the user at settings. */
    data object NotConfigured : ContributionResult

    /** The catalog rejected the credentials. */
    data object AuthFailed : ContributionResult

    data class Failed(val message: String) : ContributionResult
}

/**
 * Publishes a product the user typed in by hand to a shared open catalog, so the next
 * person scanning that barcode finds it.
 *
 * This sends data to a public third-party database and is therefore always opt-in and
 * confirmed per product by the caller — never automatic. Only the barcode, name, brand
 * and quantity are ever sent: never the user's stock, expiry dates, locations or history.
 */
interface CatalogContributor {
    /** True when the user has configured an account, i.e. contributing is possible. */
    suspend fun isConfigured(): Boolean

    /**
     * Asks the catalog whether the stored account actually works, without publishing
     * anything.
     *
     * Until this existed there was no way to find out at all: the account is only ever
     * exercised at the end of one narrow path (scan a code the catalog does not know, add
     * the product by hand, save), and a failure there was reported as a rejected product
     * rather than a rejected login. Entering correct credentials and seeing nothing happen
     * was indistinguishable from entering wrong ones.
     */
    suspend fun checkAccount(): ContributionResult

    suspend fun contribute(product: CatalogProduct): ContributionResult
}
