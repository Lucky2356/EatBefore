package com.eatbefore.domain.notification

/**
 * Whether [hour] (0..23) falls within the quiet-hours window [startHour, endHour).
 * Handles windows that wrap past midnight (e.g. 22 → 8). A zero-length window (start ==
 * end) is treated as "no quiet time".
 */
fun isWithinQuietHours(hour: Int, startHour: Int, endHour: Int): Boolean {
    if (startHour == endHour) return false
    return if (startHour < endHour) {
        hour in startHour until endHour
    } else {
        hour >= startHour || hour < endHour
    }
}

/**
 * When the daily reminder should actually go out: the chosen time, or the end of quiet
 * hours when the chosen time falls inside them. Without this a reminder set for 7:00 with
 * quiet hours until 8:00 was skipped every single day, and nothing said so.
 */
fun reminderTime(hour: Int, minute: Int, quietEnabled: Boolean, quietStart: Int, quietEnd: Int): Pair<Int, Int> =
    if (quietEnabled && isWithinQuietHours(hour, quietStart, quietEnd)) quietEnd to 0 else hour to minute
