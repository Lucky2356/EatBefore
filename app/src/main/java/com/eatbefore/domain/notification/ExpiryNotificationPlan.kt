package com.eatbefore.domain.notification

/**
 * A batched summary of what needs attention today. The notifier turns this into a single
 * notification ("Сегодня стоит проверить N продуктов") rather than one per item, to avoid
 * spam (prompt: combine notifications, no daily spam).
 */
data class ExpiryNotificationPlan(val expiredCount: Int, val todayCount: Int, val soonCount: Int) {
    val total: Int get() = expiredCount + todayCount + soonCount
    val hasContent: Boolean get() = total > 0

    companion object {
        val EMPTY = ExpiryNotificationPlan(0, 0, 0)
    }
}
