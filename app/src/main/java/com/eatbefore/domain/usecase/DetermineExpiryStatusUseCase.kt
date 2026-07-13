package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.ExpiryStatus
import com.eatbefore.domain.model.InventoryBatch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Derives the visual [ExpiryStatus] from an effective expiration date relative to today.
 * The "soon" window is configurable (user setting, default 3 days).
 */
class DetermineExpiryStatusUseCase @Inject constructor() {

    fun forDate(
        effectiveExpiration: LocalDate?,
        today: LocalDate,
        soonThresholdDays: Int = DEFAULT_SOON_THRESHOLD_DAYS,
    ): ExpiryStatus {
        if (effectiveExpiration == null) return ExpiryStatus.NO_DATE
        require(soonThresholdDays >= 0) { "soonThresholdDays must be >= 0" }
        return when {
            effectiveExpiration.isBefore(today) -> ExpiryStatus.EXPIRED
            effectiveExpiration.isEqual(today) -> ExpiryStatus.EXPIRES_TODAY
            !effectiveExpiration.isAfter(today.plusDays(soonThresholdDays.toLong())) ->
                ExpiryStatus.EXPIRING_SOON

            else -> ExpiryStatus.FRESH
        }
    }

    operator fun invoke(
        batch: InventoryBatch,
        today: LocalDate,
        soonThresholdDays: Int = DEFAULT_SOON_THRESHOLD_DAYS,
    ): ExpiryStatus = forDate(batch.effectiveExpirationDate, today, soonThresholdDays)

    companion object {
        const val DEFAULT_SOON_THRESHOLD_DAYS = 3
    }
}
