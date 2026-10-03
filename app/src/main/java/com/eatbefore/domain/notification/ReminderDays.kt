package com.eatbefore.domain.notification

/**
 * How far ahead one product may ask to be reminded about, instead of the general «скоро»
 * window. A short list rather than a free number: these are the answers people actually
 * give, and a fixed ceiling lets the daily check load everything it might need at once.
 */
object ReminderDays {
    // The numbers are the data here; naming each would only repeat it.
    @Suppress("MagicNumber")
    val OPTIONS: List<Int> = listOf(1, 2, 3, 5, 7, 14, 30)

    /** The longest lead any product can ask for; the daily check looks this far ahead. */
    val MAX: Int = OPTIONS.max()
}
