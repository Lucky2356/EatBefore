package com.eatbefore.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CalculateExpirationAfterOpeningUseCaseTest {

    private val useCase = CalculateExpirationAfterOpeningUseCase()
    private val opened = LocalDate.of(2026, 7, 14)

    @Test
    fun nullRecommendation_returnsNull() {
        assertNull(useCase.invoke(opened, null, printedExpiration = LocalDate.of(2026, 8, 1)))
    }

    @Test
    fun addsDays_whenBeforePrinted() {
        val result = useCase.invoke(opened, 3, printedExpiration = LocalDate.of(2026, 8, 1))
        assertEquals(LocalDate.of(2026, 7, 17), result)
    }

    @Test
    fun capsToPrinted_whenAfterOpeningExceedsIt() {
        val result = useCase.invoke(opened, 30, printedExpiration = LocalDate.of(2026, 7, 20))
        assertEquals(LocalDate.of(2026, 7, 20), result)
    }

    @Test
    fun noPrinted_usesAfterOpening() {
        val result = useCase.invoke(opened, 5, printedExpiration = null)
        assertEquals(LocalDate.of(2026, 7, 19), result)
    }
}
