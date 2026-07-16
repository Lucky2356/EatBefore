package com.eatbefore.feature.history

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.EmptyState
import com.eatbefore.core.designsystem.format.formatDateTime
import com.eatbefore.domain.model.EventType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undoLast) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = stringResource(R.string.history_undo_last),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.filter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text(stringResource(R.string.history_filter_all)) },
                )
                listOf(
                    EventType.ADDED,
                    EventType.CONSUMED,
                    EventType.DISCARDED,
                    EventType.MOVED,
                    EventType.OPENED,
                    EventType.RESTORED,
                ).forEach { type ->
                    FilterChip(
                        selected = state.filter == type,
                        onClick = { viewModel.setFilter(type) },
                        label = { Text(eventLabel(type)) },
                    )
                }
            }

            if (state.events.isEmpty()) {
                EmptyState(message = stringResource(R.string.history_empty))
            } else {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                // Grow the page once the user is within a few rows of the end.
                val shouldLoadMore by androidx.compose.runtime.remember {
                    androidx.compose.runtime.derivedStateOf {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo
                            .lastOrNull()?.index ?: 0
                        lastVisible >= listState.layoutInfo.totalItemsCount - 5
                    }
                }
                androidx.compose.runtime.LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) viewModel.loadMore()
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.events, key = { it.id }) { event ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(eventLabel(event.eventType), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        formatDateTime(event.createdAt),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (viewModel.isRestorable(event)) {
                                    TextButton(onClick = { viewModel.restore(event) }) {
                                        Text(stringResource(R.string.history_action_restore))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
