package com.eatbefore.feature.inventory

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A filter one screen asks the inventory to open with.
 *
 * The inventory is a bottom-navigation tab with no route arguments, and giving it some
 * would mean the tab button and a targeted jump navigate to two different routes — a
 * split that tends to end in duplicated back-stack entries. A one-shot request is smaller
 * and does exactly what it says: whoever reads it clears it, so returning to the tab later
 * shows the whole list again.
 */
@Singleton
class InventoryFilterRequest @Inject constructor() {

    private val _pending = MutableStateFlow<InventoryStatusFilter?>(null)

    /**
     * A flow rather than a value read at construction: the inventory tab keeps its
     * ViewModel alive between visits, so a request made while it already exists would
     * otherwise never be seen.
     */
    val pending: StateFlow<InventoryStatusFilter?> = _pending.asStateFlow()

    fun request(filter: InventoryStatusFilter) {
        _pending.value = filter
    }

    fun consume() {
        _pending.value = null
    }
}
