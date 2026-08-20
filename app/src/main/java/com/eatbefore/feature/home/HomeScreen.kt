package com.eatbefore.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.AnnouncedSnackbarHost
import com.eatbefore.core.designsystem.component.AppCard
import com.eatbefore.core.designsystem.component.AppTopBar
import com.eatbefore.core.designsystem.component.EmptyState
import com.eatbefore.core.designsystem.component.QuickActionButton
import com.eatbefore.core.designsystem.component.animatedItem
import com.eatbefore.core.designsystem.format.remainingText
import com.eatbefore.core.designsystem.format.storageDisplayName
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.LocalStatusColors
import com.eatbefore.core.designsystem.theme.Shapes
import com.eatbefore.feature.common.InventoryRowCard
import com.eatbefore.feature.common.InventoryRowUi
import com.eatbefore.feature.common.QuickAction
import com.eatbefore.feature.common.headingSaysItAll
import com.eatbefore.feature.common.rememberQuickActionHandler
import com.eatbefore.feature.common.timeline

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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { AnnouncedSnackbarHost(snackbarHost) },
        topBar = {
            AppTopBar(
                title = stringResource(R.string.home_title),
                scrollBehavior = scrollBehavior,
                titleContent = {
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            item(key = "quick-actions") {
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
            // Kept as a permanent, keyed item so that going from "nothing to do" to
            // "one thing to do" is something the eye can catch rather than a jump.
            item(key = "attention") {
                AnimatedVisibility(visible = state.needsAttentionCount > 0) {
                    AttentionBanner(
                        count = state.needsAttentionCount,
                        onClick = {
                            viewModel.requestAttentionFilter()
                            onOpenInventory()
                        },
                    )
                }
            }

            state.eatFirst?.let { row ->
                item(key = "eat-first") {
                    EatFirstCard(
                        modifier = animatedItem(),
                        row = row,
                        onOpen = { onOpenBatch(row.batchId) },
                        onFinished = { onQuickAction(QuickAction.FINISHED, row.batchId) },
                    )
                }
            }

            if (state.totalCount == 0 && !state.isLoading) {
                item(key = "empty") {
                    EmptyState(
                        message = stringResource(R.string.home_empty),
                        actionLabel = stringResource(R.string.home_quick_scan),
                        onAction = onScan,
                    )
                }
            }

            // The two sections that used to sit here — what is going off, and what was
            // added lately — are one axis now. "Recently added" was a list ordered by a
            // clock nobody reads; every row it held is on the axis, under the day it
            // actually runs out.
            timeline(
                groups = state.timeline,
                itemSpacing = Dimens.spaceMd,
            ) { row, bucket, rowModifier ->
                InventoryRowCard(
                    modifier = rowModifier,
                    row = row,
                    onClick = { onOpenBatch(row.batchId) },
                    onQuickAction = { onQuickAction(it, row.batchId) },
                    showExpiry = !bucket.headingSaysItAll,
                )
            }
        }
    }
}

/**
 * The one thing the home screen leads with: how much is waiting to be dealt with, and a
 * way straight to it. Shown only when the count is above zero — see the call site.
 *
 * A tint, not a fill. This used to be `errorContainer`, which in the dark theme is pure
 * #93000A: one carton of milk due tonight painted a block of emergency red across the top
 * of the screen, louder than anything the app has to say about actual spoiled food. The
 * count is the loud part now, and it is loud by being the largest number on screen.
 */
@Composable
private fun AttentionBanner(count: Int, onClick: () -> Unit) {
    val role = LocalStatusColors.current.today

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = Shapes.row,
        containerColor = role.container,
        contentColor = role.onContainer,
        border = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null)
            Text(
                text = pluralStringResource(R.plurals.home_needs_attention, count, count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/**
 * One product and one decision: eat this before anything else, or say it is gone.
 *
 * The screen already listed what is going off; a list is a report, not an instruction, and
 * at six in the evening with the fridge open the question is which of five things to take
 * out. The row is deliberately not another [InventoryRowCard] — it carries a heading and
 * an action button, and looking like the list beneath it would hide exactly what makes it
 * different.
 *
 * It carries the product's photo where there is one. Recognising a jar on the shelf is
 * faster than reading its name, and this is the one card the user is meant to act on
 * while standing in front of the fridge.
 */
@Composable
private fun EatFirstCard(
    row: InventoryRowUi,
    onOpen: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onOpen,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                Icon(Icons.Outlined.Restaurant, contentDescription = null)
                Text(
                    text = stringResource(R.string.home_eat_first),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
            ) {
                if (row.imageUri != null) {
                    AsyncImage(
                        model = row.imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(Dimens.heroThumbnailSize)
                            .clip(Shapes.control),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                ) {
                    Text(
                        text = row.productName,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            // An opened pack is why this one was picked over a sealed one
                            // that expires sooner, so it says so rather than leaving the
                            // order unexplained.
                            if (row.isOpened) stringResource(R.string.row_opened) else null,
                            remainingText(row.remainingDays),
                            storageDisplayName(row.locationName, row.locationType),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Filled, not tonal. A tonal button takes `secondaryContainer`, which on this
            // card's `primaryContainer` is a two-percent difference — the one button the
            // screen is built around was very nearly invisible.
            Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Outlined.TaskAlt,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Dimens.spaceSm),
                )
                Text(stringResource(R.string.product_action_finished))
            }
        }
    }
}
