package com.eatbefore.testutil

import com.eatbefore.core.common.time.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Deterministic clock for tests. Defaults to a fixed instant; advanceable if needed. */
class FakeAppClock(private var current: Instant = Instant.parse("2026-07-14T10:00:00Z"), private val zone: ZoneId = ZoneOffset.UTC) :
    AppClock {
    override fun now(): Instant = current
    override fun zone(): ZoneId = zone
    override fun today(): LocalDate = LocalDate.ofInstant(current, zone)

    fun set(instant: Instant) {
        current = instant
    }
}
