package com.eatbefore.feature.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.AnnouncedSnackbarHost
import com.eatbefore.core.designsystem.component.AppTopBar
import com.eatbefore.core.designsystem.component.EmptyState
import com.eatbefore.core.designsystem.component.animatedItem
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.Shapes
import com.eatbefore.feature.common.InventoryRowCard
import com.eatbefore.feature.common.InventoryRowUi
import com.eatbefore.feature.common.QuickAction
import com.eatbefore.feature.common.RowDisplay
import com.eatbefore.feature.common.headingSaysItAll
import com.eatbefore.feature.common.rememberQuickActionHandler
import com.eatbefore.feature.common.timeline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onOpenBatch: (Long) -> Unit,
    onScanToSearch: () -> Unit,
    scannedCode: String?,
    onScannedCodeUsed: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Separate from uiState: the field must echo keystrokes without the search debounce.
    val queryText by viewModel.queryText.collectAsStateWithLifecycle()
    val quickActionSignal by viewModel.quickActionSignal.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val selection by viewModel.selection.collectAsStateWithLifecycle()

    // A code scanned at the shelf becomes the search term. Cleared straight away, or
    // returning to this tab later would silently re-run a search the user has moved on from.
    LaunchedEffect(scannedCode) {
        if (scannedCode != null) {
            viewModel.setQuery(scannedCode)
            onScannedCodeUsed()
        }
    }

    val onQuickAction = rememberQuickActionHandler(
        signal = quickActionSignal,
        snackbarHostState = snackbarHost,
        today = viewModel.today,
        onPerform = viewModel::quickAction,
        onUndo = viewModel::undoQuickAction,
        onConsume = viewModel::consumeQuickActionSignal,
        onStartSelection = viewModel::startSelection,
    )

    // Leaving selection mode is what Back means while it is on — it is a mode, and a mode
    // the system Back gesture cannot escape is a trap.
    androidx.activity.compose.BackHandler(enabled = selection != null) {
        viewModel.stopSelection()
    }

    val selected = selection
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { AnnouncedSnackbarHost(snackbarHost) },
        bottomBar = {
            // Slides in rather than appearing: it covers the bottom of the list, and a bar
            // that materialises under the thumb during a long press is easy to hit by
            // accident on the way out of the gesture.
            AnimatedVisibility(
                visible = selected != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                SelectionBar(
                    count = selected?.size ?: 0,
                    onFinished = { viewModel.applyToSelection(QuickAction.FINISHED) },
                    onDiscard = { viewModel.applyToSelection(QuickAction.DISCARD) },
                )
            }
        },
        topBar = {
            // While selecting, the bar reports the count and offers the way out — the
            // Android convention, and the only layout where the count and the actions both
            // fit without wrapping.
            AppTopBar(
                title = if (selected == null) {
                    stringResource(R.string.inventory_title)
                } else {
                    pluralStringResource(R.plurals.inventory_selected, selected.size, selected.size)
                },
                // Pinned while selecting: the count and the way out must not scroll away.
                scrollBehavior = if (selected == null) scrollBehavior else null,
                navigationIcon = {
                    if (selected != null) {
                        IconButton(onClick = viewModel::stopSelection) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_done),
                            )
                        }
                    }
                },
                actions = {
                    if (selected != null) return@AppTopBar
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Sort,
                            contentDescription = stringResource(R.string.inventory_sort),
                        )
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        SortItem(R.string.inventory_sort_expiry, InventorySort.EXPIRY, state.sort) {
                            viewModel.setSort(it)
                            sortMenuOpen = false
                        }
                        SortItem(R.string.inventory_sort_name, InventorySort.NAME, state.sort) {
                            viewModel.setSort(it)
                            sortMenuOpen = false
                        }
                        SortItem(R.string.inventory_sort_added, InventorySort.ADDED, state.sort) {
                            viewModel.setSort(it)
                            sortMenuOpen = false
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = queryText,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    // "Do we already have this?" asked at the shelf in a shop. The search
                    // has always matched barcodes; until now there was no way to get one in
                    // without typing thirteen digits.
                    if (queryText.isBlank()) {
                        IconButton(onClick = onScanToSearch) {
                            Icon(
                                Icons.Outlined.QrCodeScanner,
                                contentDescription = stringResource(R.string.inventory_search_by_code),
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.inventory_search_clear),
                            )
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.inventory_search_hint)) },
                shape = Shapes.pill,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceSm),
            )

            // One strip, not two. Place and condition are different questions, but they
            // were costing two full rows on top of the search field — three bands of
            // controls before a single product, on a screen whose whole job is the list.
            // The places collapse into one chip that opens them, which also stops the row
            // growing every time a new place is added.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceXs),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                LocationFilterChip(state = state, onSelect = viewModel::setLocation)
                VerticalDivider(modifier = Modifier.height(Dimens.iconMd))
                StatusFilterChip(R.string.inventory_filter_all, InventoryStatusFilter.ALL, state, viewModel)
                StatusFilterChip(R.string.inventory_filter_today, InventoryStatusFilter.TODAY, state, viewModel)
                StatusFilterChip(R.string.inventory_filter_expired, InventoryStatusFilter.EXPIRED, state, viewModel)
                StatusFilterChip(R.string.inventory_filter_soon, InventoryStatusFilter.SOON, state, viewModel)
                StatusFilterChip(R.string.inventory_filter_opened, InventoryStatusFilter.OPENED, state, viewModel)
            }

            if (!state.isLoading && state.rows.isEmpty()) {
                // "Nothing here" and "nothing matches" are different situations; saying
                // the stock is empty while a filter is on would be plainly wrong.
                val filtered = state.statusFilter != InventoryStatusFilter.ALL ||
                    state.selectedLocationId != null ||
                    queryText.isNotBlank()
                EmptyState(
                    message = stringResource(
                        if (filtered) R.string.inventory_filter_empty else R.string.inventory_empty,
                    ),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                ) {
                    if (state.isTimeline) {
                        timeline(
                            groups = state.timeline,
                            itemSpacing = Dimens.spaceMd,
                        ) { row, bucket, rowModifier ->
                            StockRow(
                                modifier = rowModifier,
                                row = row,
                                selection = selection,
                                onOpenBatch = onOpenBatch,
                                onQuickAction = onQuickAction,
                                onToggle = viewModel::toggleSelection,
                                display = RowDisplay(
                                    // The place is worth naming here: the axis groups by
                                    // time, so a row's place is no longer implied by the
                                    // heading above it — unless one place is already
                                    // chosen, and then the chip says it.
                                    showLocation = state.selectedLocationId == null,
                                    showExpiry = !bucket.headingSaysItAll,
                                    // The heading says "Expired"; the label only owes
                                    // the number, and the word was costing the name.
                                    conciseExpiry = true,
                                ),
                            )
                        }
                    } else {
                        items(state.rows, key = { it.batchId }) { row ->
                            StockRow(
                                modifier = animatedItem(),
                                row = row,
                                selection = selection,
                                onOpenBatch = onOpenBatch,
                                onQuickAction = onQuickAction,
                                onToggle = viewModel::toggleSelection,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * What to do with everything ticked. Sits at the bottom, within thumb reach, and holds the
 * only way out of the mode besides Back — a mode with no visible exit is a trap.
 */
@Composable
private fun SelectionBar(
    count: Int,
    onFinished: () -> Unit,
    onDiscard: () -> Unit,
) {
    Surface(tonalElevation = Dimens.spaceXs) {
        Row(
            // No navigationBarsPadding here. This bar sits inside the app's own Scaffold,
            // above the navigation tabs, and those already carry the system inset —
            // claiming it a second time left a band of empty tint under the buttons.
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            TextButton(
                onClick = onFinished,
                enabled = count > 0,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.product_action_finished)) }
            TextButton(
                onClick = onDiscard,
                enabled = count > 0,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.product_action_discard)) }
        }
    }
}

/**
 * One stock row, which means something different depending on the mode: normally a tap
 * opens the product, while a selection is running it ticks the row instead.
 */

@Composable
private fun StockRow(
    row: InventoryRowUi,
    selection: Set<Long>?,
    onOpenBatch: (Long) -> Unit,
    onQuickAction: (QuickAction, Long) -> Unit,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
    display: RowDisplay = RowDisplay(),
) {
    InventoryRowCard(
        modifier = modifier,
        display = display,
        row = row,
        onClick = {
            if (selection == null) onOpenBatch(row.batchId) else onToggle(row.batchId)
        },
        // No long-press menu while selecting: the row already has one job.
        onQuickAction = if (selection == null) {
            { onQuickAction(it, row.batchId) }
        } else {
            null
        },
        selected = selection?.contains(row.batchId),
    )
}

/**
 * The place filter as one chip that opens the list of places.
 *
 * It used to be a scrolling row of one chip per place. That row grew with every place the
 * household added and, being the widest of the three control strips, was the one that
 * pushed the products off the screen. As a chip it also states the current answer, which a
 * row of equal-looking chips only did if the selected one happened to be scrolled into view.
 */
@Composable
private fun LocationFilterChip(state: InventoryUiState, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = state.locations.firstOrNull { it.id == state.selectedLocationId }

    Box {
        FilterChip(
            selected = state.selectedLocationId != null,
            onClick = { open = true },
            label = { Text(selected?.displayName() ?: stringResource(R.string.inventory_all_locations)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.inventory_all_locations)) },
                onClick = {
                    onSelect(null)
                    open = false
                },
            )
            state.locations.forEach { location ->
                DropdownMenuItem(
                    text = { Text(location.displayName()) },
                    onClick = {
                        onSelect(location.id)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusFilterChip(
    labelRes: Int,
    value: InventoryStatusFilter,
    state: InventoryUiState,
    viewModel: InventoryViewModel,
) {
    FilterChip(
        selected = state.statusFilter == value,
        onClick = { viewModel.setStatusFilter(value) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun SortItem(
    labelRes: Int,
    value: InventorySort,
    current: InventorySort,
    onSelected: (InventorySort) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = { onSelected(value) },
        trailingIcon = {
            if (value == current) {
                // Decorative: the menu item's own text already names the sort order.
                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null)
            }
        },
    )
}
