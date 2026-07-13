package com.eatbefore.domain.usecase

import java.time.LocalDate
import javax.inject.Inject

/**
 * Computes the "use by" date after a package is opened: opened date + recommended days.
 * If a printed expiration exists, the result is capped to it — opening never *extends*
 * shelf life. Returns null when there is no recommendation.
 */
class CalculateExpirationAfterOpeningUseCase @Inject constructor() {

    operator fun invoke(
        openedDate: LocalDate,
        recommendedUseAfterOpeningDays: Int?,
        printedExpiration: LocalDate?,
    ): LocalDate? {
        val days = recommendedUseAfterOpeningDays ?: return null
        require(days >= 0) { "recommendedUseAfterOpeningDays must be >= 0" }
        val afterOpening = openedDate.plusDays(days.toLong())
        return when {
            printedExpiration == null -> afterOpening
            afterOpening.isBefore(printedExpiration) -> afterOpening
            else -> printedExpiration
        }
    }
}
