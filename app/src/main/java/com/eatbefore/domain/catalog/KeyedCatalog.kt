package com.eatbefore.domain.catalog

/**
 * A catalog that works with the user's own key, and can say whether that key works.
 * Settings ask it directly: a refused key otherwise looks exactly like a catalog that
 * simply does not know the product.
 */
interface KeyedCatalog {
    suspend fun checkKey(): KeyCheckResult
}

enum class KeyCheckResult {
    WORKS,
    REJECTED,
    QUOTA_USED,

    /** No key stored, or it no longer decrypts (the app was reinstalled). */
    NOT_SET,

    /** No answer — offline or the service is down. Says nothing about the key. */
    UNREACHABLE,
}
