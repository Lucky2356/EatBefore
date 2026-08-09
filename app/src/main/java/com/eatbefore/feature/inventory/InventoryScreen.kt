package com.eatbefore.feature.inventory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.AnnouncedSnackbarHost
import com.eatbefore.core.designsystem.component.EmptyState
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.feature.common.InventoryRowCard
import com.eatbefore.feature.common.rememberQuickActionHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onOpenBatch: (Long) -> Unit,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Separate from uiState: the field must echo keystrokes without the search debounce.
    val queryText by viewModel.queryText.collectAsStateWithLifecycle()
    val quickActionSignal by viewModel.quickActionSignal.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val onQuickAction = rememberQuickActionHandler(
        signal = quickActionSignal,
        snackbarHostState = snackbarHost,
        today = viewModel.today,
        onPerform = viewModel::quickAction,
        onUndo = viewModel::undoQuickAction,
        onConsume = viewModel::consumeQuickActionSignal,
    )

    Scaffold(
        snackbarHost = { AnnouncedSnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_title)) },
                actions = {
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
                placeholder = { Text(stringResource(R.string.inventory_search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceSm),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.spaceLg),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                FilterChip(
                    selected = state.selectedLocationId == null,
                    onClick = { viewModel.setLocation(null) },
                    label = { Text(stringResource(R.string.inventory_all_locations)) },
                )
                state.locations.forEach { location ->
                    FilterChip(
                        selected = state.selectedLocationId == location.id,
                        onClick = { viewModel.setLocation(location.id) },
                        label = { Text(location.displayName()) },
                    )
                }
            }

            // A second row rather than one long one: place and condition are different
            // questions ("what is in the freezer" vs "what must I deal with today"), and
            // mixing them into a single strip makes both harder to find.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceXs),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                StatusFilterChip(R.string.inventory_filter_all, InventoryStatusFilter.ALL, state, viewModel)
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
                    items(state.rows, key = { it.batchId }) { row ->
                        InventoryRowCard(
                            row = row,
                            onClick = { onOpenBatch(row.batchId) },
                            onQuickAction = { onQuickAction(it, row.batchId) },
                        )
                    }
                }
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
