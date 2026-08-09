package com.eatbefore.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.AnnouncedSnackbarHost
import com.eatbefore.core.designsystem.component.EmptyState
import com.eatbefore.core.designsystem.component.QuickActionButton
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.Shapes
import com.eatbefore.feature.common.InventoryRowCard
import com.eatbefore.feature.common.rememberQuickActionHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onAddManual: () -> Unit,
    onOpenShopping: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenBatch: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val quickActionSignal by viewModel.quickActionSignal.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val today = remember {
        viewModel.today.format(
            java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault()),
        ).replaceFirstChar { it.uppercase() }
    }

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
                title = {
                    Column {
                        Text(stringResource(R.string.home_title))
                        Text(
                            today,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    QuickActionButton(
                        Icons.Outlined.QrCodeScanner,
                        stringResource(R.string.home_quick_scan),
                        onScan,
                    )
                    QuickActionButton(
                        Icons.Outlined.AddCircleOutline,
                        stringResource(R.string.home_quick_add_manual),
                        onAddManual,
                    )
                    QuickActionButton(
                        Icons.Outlined.ShoppingCart,
                        stringResource(R.string.home_quick_shopping),
                        onOpenShopping,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                ) {
                    HomeStatTile(
                        value = state.totalCount,
                        label = stringResource(R.string.home_stat_total),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    HomeStatTile(
                        value = state.expiringSoon.size,
                        label = stringResource(R.string.home_stat_expiring),
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    // Only when there is something to answer for. A permanent "0 expired"
                    // tile is noise; a red one that appears is a message.
                    if (state.expiredCount > 0) {
                        HomeStatTile(
                            value = state.expiredCount,
                            label = stringResource(R.string.home_stat_expired),
                            container = MaterialTheme.colorScheme.errorContainer,
                            onContainer = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.requestExpiredFilter()
                                onOpenInventory()
                            },
                        )
                    }
                }
            }

            if (state.totalCount == 0 && !state.isLoading) {
                item {
                    EmptyState(
                        message = stringResource(R.string.home_empty),
                        actionLabel = stringResource(R.string.home_quick_scan),
                        onAction = onScan,
                    )
                }
            }

            if (state.expiringSoon.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.home_expiring_section), Icons.Outlined.Schedule) }
                items(state.expiringSoon, key = { "exp-${it.batchId}" }) { row ->
                    InventoryRowCard(
                        row = row,
                        onClick = { onOpenBatch(row.batchId) },
                        onQuickAction = { onQuickAction(it, row.batchId) },
                    )
                }
            }

            if (state.recent.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.home_recent_section), Icons.Outlined.AddCircleOutline) }
                items(state.recent, key = { "rec-${it.batchId}" }) { row ->
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

@Composable
private fun HomeStatTile(
    value: Int,
    label: String,
    container: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    androidx.compose.material3.Card(
        modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
        shape = Shapes.card,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLg)) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = onContainer,
            )
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = onContainer)
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        modifier = Modifier.padding(top = Dimens.spaceSm),
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = title, style = MaterialTheme.typography.titleMedium)
    }
}
