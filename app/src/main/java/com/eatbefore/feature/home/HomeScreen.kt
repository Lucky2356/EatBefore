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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.ui.res.pluralStringResource
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
                            // The stock count used to be a tile of its own. It is context,
                            // not a call to action, and it belongs next to the date.
                            "$today · " + pluralStringResource(
                                R.plurals.home_products_at_home,
                                state.totalCount,
                                state.totalCount,
                            ),
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

            // One line, and only when it has something to say. The band of tiles it
            // replaced spent a quarter of the screen announcing "nothing to do".
            if (state.needsAttentionCount > 0) {
                item {
                    AttentionBanner(
                        count = state.needsAttentionCount,
                        onClick = {
                            viewModel.requestAttentionFilter()
                            onOpenInventory()
                        },
                    )
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

/**
 * The one thing the home screen leads with: how much is waiting to be dealt with, and a
 * way straight to it. Shown only when the count is above zero — see the call site.
 */
@Composable
private fun AttentionBanner(count: Int, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = Shapes.card,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLg),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            androidx.compose.material3.Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = pluralStringResource(R.plurals.home_needs_attention, count, count),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
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
