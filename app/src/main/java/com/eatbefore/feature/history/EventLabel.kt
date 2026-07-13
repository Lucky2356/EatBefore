package com.eatbefore.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eatbefore.R
import com.eatbefore.domain.model.EventType

/** Localized label for a history [EventType]. */
@Composable
fun eventLabel(type: EventType): String = stringResource(
    when (type) {
        EventType.ADDED -> R.string.event_added
        EventType.UPDATED -> R.string.event_updated
        EventType.OPENED -> R.string.event_opened
        EventType.QUANTITY_CHANGED -> R.string.event_quantity_changed
        EventType.MOVED -> R.string.event_moved
        EventType.CONSUMED -> R.string.event_consumed
        EventType.DISCARDED -> R.string.event_discarded
        EventType.EXPIRED -> R.string.event_expired
        EventType.RESTORED -> R.string.event_restored
        EventType.ADDED_TO_SHOPPING_LIST -> R.string.event_added_to_shopping
        EventType.REMOVED_FROM_SHOPPING_LIST -> R.string.event_removed_from_shopping
    },
)
