package com.eatbefore.domain.notification

/**
 * A batched summary of what needs attention today. The notifier turns this into a single
 * notification ("Сегодня стоит проверить N продуктов") rather than one per item, to avoid
 * spam (prompt: combine notifications, no daily spam).
 */
data class ExpiryNotificationPlan(
    val expiredCount: Int,
    val todayCount: Int,
    val soonCount: Int,
    /**
     * The one batch this is about, when it is about exactly one.
     *
     * With a single product the shade can offer "ate it" and "threw it out" directly, and
     * the whole errand is over without unlocking the phone. With five it cannot: the
     * buttons would have to pick one of them silently, and acting on the wrong carton of
     * milk is worse than opening the app.
     */
    val singleBatchId: Long? = null,
    val singleProductName: String? = null,
) {
    val total: Int get() = expiredCount + todayCount + soonCount
    val hasContent: Boolean get() = total > 0

    companion object {
        val EMPTY = ExpiryNotificationPlan(0, 0, 0)
    }
}
