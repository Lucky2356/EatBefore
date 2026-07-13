package com.eatbefore.domain.model

/**
 * Visual freshness state derived from a batch's effective expiration date relative to
 * "today". Distinct from [BatchStatus]: this is about time, not user actions. Rendered
 * as a badge with an icon + label so meaning is never color-only (accessibility).
 */
enum class ExpiryStatus {
    /** Comfortably within shelf life. */
    FRESH,

    /** Within the user-configured "expiring soon" window (default a few days). */
    EXPIRING_SOON,

    /** Expires today. */
    EXPIRES_TODAY,

    /** Past its effective expiration date. */
    EXPIRED,

    /** No expiration date recorded. */
    NO_DATE,
}
