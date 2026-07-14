package com.eatbefore.core.common.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Abstraction over the system clock so time-dependent logic (expiry, use-after-opening)
 * can be tested deterministically. Production uses [SystemAppClock]; tests inject a fake.
 */
interface AppClock {
    fun now(): Instant
    fun zone(): ZoneId

    // atZone(...) instead of LocalDate.ofInstant: the latter needs API 34 (minSdk is 26).
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}

class SystemAppClock : AppClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
