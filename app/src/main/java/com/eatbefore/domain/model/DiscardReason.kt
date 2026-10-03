package com.eatbefore.domain.model

/**
 * Why food went in the bin, asked when it is thrown out.
 *
 * The count of what was wasted says how much; the reason says what to change. «Забыли»
 * means the reminders need to come earlier, «купили много» means smaller packs, «не
 * понравилось» means not buying it again — each points somewhere different, and the
 * analytics screen shows which one dominates.
 *
 * Stored by name in the event's reason column, which already travels to the other phone
 * and into the backup. Optional: skipping the question is always allowed.
 */
enum class DiscardReason {
    SPOILED,
    FORGOT,
    TOO_MUCH,
    DISLIKED,
    ;

    companion object {
        fun fromCode(code: String?): DiscardReason? = entries.firstOrNull { it.name == code }
    }
}
