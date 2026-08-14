package com.eatbefore.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eatbefore.R
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent

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

/**
 * Who did this, or null when it was this phone.
 *
 * Only actions that arrived from the other household member are signed. In a two-person
 * household "not me" already names the person, and labelling our own rows «Это устройство»
 * would put a word on every line to say nothing. [peerNames] comes from the journals we
 * have merged; an id we have no name for yet still deserves an honest answer.
 */
@Composable
fun eventAuthor(event: InventoryEvent, peerNames: Map<String, String>): String? {
    if (event.deviceId.isBlank()) return null
    return peerNames[event.deviceId] ?: stringResource(R.string.history_other_device)
}
