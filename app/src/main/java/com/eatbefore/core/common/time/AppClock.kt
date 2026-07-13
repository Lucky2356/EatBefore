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
    fun today(): LocalDate = LocalDate.ofInstant(now(), zone())
}

class SystemAppClock : AppClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
