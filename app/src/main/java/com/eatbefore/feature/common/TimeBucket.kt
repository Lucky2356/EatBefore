package com.eatbefore.feature.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.eatbefore.R
import com.eatbefore.core.designsystem.theme.LocalStatusColors

/**
 * How far off a batch is, in the words a person would use standing at the fridge.
 *
 * The app already had [com.eatbefore.domain.model.ExpiryStatus], but that answers "how
 * alarmed should this row look" — three of its five values collapse everything from
 * tomorrow to next year into one. This answers "when", which is what the lists are now
 * ordered and grouped by, and it needs the distinctions ExpiryStatus deliberately drops.
 *
 * Declaration order is display order; the grouping sorts by [ordinal] and nothing else.
 */
enum class TimeBucket {
    EXPIRED,
    TODAY,
    TOMORROW,
    THIS_WEEK,
    LATER,

    /** No date recorded — last, because it is not a point on the axis at all. */
    NO_DATE,
}

/** Which bucket this row falls in, from its remaining days alone. */
fun InventoryRowUi.timeBucket(): TimeBucket {
    val days = remainingDays ?: return TimeBucket.NO_DATE
    return when {
        days < 0 -> TimeBucket.EXPIRED
        days == 0L -> TimeBucket.TODAY
        days == 1L -> TimeBucket.TOMORROW
        days <= THIS_WEEK_DAYS -> TimeBucket.THIS_WEEK
        else -> TimeBucket.LATER
    }
}

/** Rows sharing one bucket, in the order the bucket is declared. */
data class TimelineGroup(val bucket: TimeBucket, val rows: List<InventoryRowUi>)

/**
 * Splits rows into buckets, dropping the empty ones.
 *
 * Empty buckets are dropped rather than shown greyed out: a heading with nothing under it
 * is a promise the list does not keep, and on a well-kept fridge four of the six would be
 * empty on any given day.
 */
fun List<InventoryRowUi>.groupByTime(): List<TimelineGroup> = groupBy { it.timeBucket() }
    .entries
    .sortedBy { it.key.ordinal }
    .map { TimelineGroup(it.key, it.value) }

/** The heading shown for this bucket. */
val TimeBucket.labelRes: Int
    get() = when (this) {
        TimeBucket.EXPIRED -> R.string.timeline_expired
        TimeBucket.TODAY -> R.string.timeline_today
        TimeBucket.TOMORROW -> R.string.timeline_tomorrow
        TimeBucket.THIS_WEEK -> R.string.timeline_this_week
        TimeBucket.LATER -> R.string.timeline_later
        TimeBucket.NO_DATE -> R.string.timeline_no_date
    }

/**
 * True when a row's own expiry label would say nothing the heading above it has not.
 *
 * "Today", "tomorrow" and "no date" are the whole of what the label would carry, and the
 * list printed each of them twice within a centimetre of itself. "Expired" and "later"
 * are not: three days ago and twenty-seven days out are facts no heading holds.
 */
val TimeBucket.headingSaysItAll: Boolean
    get() = this == TimeBucket.TODAY || this == TimeBucket.TOMORROW || this == TimeBucket.NO_DATE

/**
 * The colour of the bucket's node and heading.
 *
 * The two buckets that need no reaction — later, and no date at all — take the muted
 * body colour rather than a hue of their own. Four coloured nodes down the rail already
 * say everything; six would say nothing.
 */
@Composable
@ReadOnlyComposable
fun TimeBucket.markerColor(): Color {
    val status = LocalStatusColors.current
    return when (this) {
        TimeBucket.EXPIRED -> status.expired.content
        TimeBucket.TODAY -> status.today.content
        TimeBucket.TOMORROW -> status.soon.content
        TimeBucket.THIS_WEEK -> status.fresh.content
        TimeBucket.LATER, TimeBucket.NO_DATE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Buckets past this many days away are simply "later". Seven rather than the user's
 * "expiring soon" window on purpose: this is a calendar word, not a warning threshold,
 * and "on this week" has to mean a week whatever the notification settings say.
 */
private const val THIS_WEEK_DAYS = 7L
