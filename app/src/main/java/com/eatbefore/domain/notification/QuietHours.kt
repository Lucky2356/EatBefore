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
