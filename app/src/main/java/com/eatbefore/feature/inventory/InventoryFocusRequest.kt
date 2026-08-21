package com.eatbefore.feature.inventory

import com.eatbefore.feature.common.TimeBucket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A division of the time axis that one screen asks the inventory to open on.
 *
 * The inventory is a bottom-navigation tab with no route arguments, and giving it some
 * would mean the tab button and a targeted jump navigate to two different routes — a
 * split that tends to end in duplicated back-stack entries. A one-shot request is smaller
 * and does exactly what it says: whoever reads it clears it, so returning to the tab later
 * shows the list where it was left.
 *
 * A bucket rather than a status filter. This used to carry
 * [InventoryStatusFilter], and the home screen's banner asked for the one that means
 * "expired plus due today" — a set no single heading on the axis names. Tapping a heading
 * that counted one and landing on a list showing two is exactly how a number stops being
 * believed. Both screens draw the same axis, so pointing at a division of it cannot
 * disagree with itself.
 */
@Singleton
class InventoryFocusRequest @Inject constructor() {

    private val _pending = MutableStateFlow<TimeBucket?>(null)

    /**
     * A flow rather than a value read at construction: the inventory tab keeps its
     * ViewModel alive between visits, so a request made while it already exists would
     * otherwise never be seen.
     */
    val pending: StateFlow<TimeBucket?> = _pending.asStateFlow()

    fun request(bucket: TimeBucket) {
        _pending.value = bucket
    }

    fun consume() {
        _pending.value = null
    }
}
