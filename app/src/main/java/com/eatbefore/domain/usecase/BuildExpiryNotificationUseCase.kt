package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.ExpiryStatus
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.notification.ExpiryNotificationPlan
import java.time.LocalDate
import javax.inject.Inject

/**
 * Turns the set of soon-to-expire/expired batches into a batched [ExpiryNotificationPlan].
 * Pure and testable: callers pass the item snapshot, today, and the "soon" window.
 */
class BuildExpiryNotificationUseCase @Inject constructor(private val determineExpiryStatus: DetermineExpiryStatusUseCase) {

    operator fun invoke(
        items: List<InventoryItem>,
        today: LocalDate,
        soonThresholdDays: Int,
    ): ExpiryNotificationPlan {
        var expired = 0
        var todayCount = 0
        var soon = 0
        val counted = mutableListOf<InventoryItem>()
        for (item in items) {
            val status = determineExpiryStatus.forDate(item.batch.effectiveExpirationDate, today, soonThresholdDays)
            val relevant = when (status) {
                ExpiryStatus.EXPIRED -> {
                    expired++
                    true
                }
                ExpiryStatus.EXPIRES_TODAY -> {
                    todayCount++
                    true
                }
                ExpiryStatus.EXPIRING_SOON -> {
                    soon++
                    true
                }
                ExpiryStatus.FRESH, ExpiryStatus.NO_DATE -> false
            }
            if (relevant) counted += item
        }
        // Named only when it is the whole notification: the shade's buttons act on one
        // batch, and they must act on the batch the text is about.
        val single = counted.singleOrNull()
        return ExpiryNotificationPlan(
            expiredCount = expired,
            todayCount = todayCount,
            soonCount = soon,
            singleBatchId = single?.batch?.id,
            singleProductName = single?.product?.name,
        )
    }
}
