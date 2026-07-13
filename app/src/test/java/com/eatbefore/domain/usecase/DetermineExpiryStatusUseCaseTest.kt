package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.ExpiryStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DetermineExpiryStatusUseCaseTest {

    private val useCase = DetermineExpiryStatusUseCase()
    private val today = LocalDate.of(2026, 7, 14)

    @Test
    fun nullDate_isNoDate() {
        assertEquals(ExpiryStatus.NO_DATE, useCase.forDate(null, today))
    }

    @Test
    fun pastDate_isExpired() {
        assertEquals(ExpiryStatus.EXPIRED, useCase.forDate(today.minusDays(1), today))
    }

    @Test
    fun sameDay_isExpiresToday() {
        assertEquals(ExpiryStatus.EXPIRES_TODAY, useCase.forDate(today, today))
    }

    @Test
    fun withinThreshold_isExpiringSoon() {
        assertEquals(
            ExpiryStatus.EXPIRING_SOON,
            useCase.forDate(today.plusDays(3), today, soonThresholdDays = 3),
        )
    }

    @Test
    fun beyondThreshold_isFresh() {
        assertEquals(
            ExpiryStatus.FRESH,
            useCase.forDate(today.plusDays(4), today, soonThresholdDays = 3),
        )
    }
}
