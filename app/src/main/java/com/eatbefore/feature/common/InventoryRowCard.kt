package com.eatbefore.feature.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TakeoutDining
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.StatusBadge
import com.eatbefore.core.designsystem.format.formatQuantity
import com.eatbefore.core.designsystem.format.remainingText
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.domain.model.StorageType

private fun StorageType.icon(): ImageVector = when (this) {
    StorageType.FRIDGE -> Icons.Outlined.Kitchen
    StorageType.FREEZER -> Icons.Outlined.AcUnit
    StorageType.CUPBOARD -> Icons.Outlined.TakeoutDining
    StorageType.PANTRY -> Icons.Outlined.Warehouse
    StorageType.OTHER -> Icons.Outlined.Place
}

/**
 * A tappable card summarizing one inventory batch.
 *
 * Passing [onQuickAction] adds a long-press menu. Writing off what you just ate is the
 * most frequent thing the app is asked to do, and without it that took four steps: open
 * the card, wait for it to load, press, go back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InventoryRowCard(
    row: InventoryRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onQuickAction: ((QuickAction) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (onQuickAction == null) {
                    null
                } else {
                    {
                        // The phone confirms the press before the menu draws — the finger
                        // is still covering the card at that moment.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    }
                },
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (row.imageUri != null) {
                    // Catalog photo thumbnail; the location icon is the fallback.
                    coil.compose.AsyncImage(
                        model = row.imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = row.locationType.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.productName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val locationLabel =
                    com.eatbefore.core.designsystem.format.storageDisplayName(
                        row.locationName,
                        row.locationType,
                    )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                ) {
                    // An open pack is on a different clock from an unopened one, and
                    // standing at the fridge is exactly when that matters.
                    if (row.isOpened) {
                        Icon(
                            imageVector = Icons.Outlined.LockOpen,
                            contentDescription = stringResource(R.string.row_opened),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${formatQuantity(row.quantity, row.unit)} · $locationLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                remainingText(row.remainingDays)?.let { remaining ->
                    Text(
                        text = remaining,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusBadge(status = row.expiryStatus)

            if (onQuickAction != null) {
                QuickActionMenu(
                    expanded = menuOpen,
                    isOpened = row.isOpened,
                    onDismiss = { menuOpen = false },
                    onAction = {
                        menuOpen = false
                        onQuickAction(it)
                    },
                )
            }
        }
    }
}

/** The long-press menu: the four daily actions plus buying another package. */
@Composable
private fun QuickActionMenu(
    expanded: Boolean,
    isOpened: Boolean,
    onDismiss: () -> Unit,
    onAction: (QuickAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (!isOpened) {
            QuickActionItem(
                labelRes = R.string.product_action_open,
                icon = Icons.Outlined.LockOpen,
                onClick = { onAction(QuickAction.OPEN) },
            )
        }
        QuickActionItem(
            labelRes = R.string.product_action_decrement,
            icon = Icons.Outlined.RemoveCircleOutline,
            onClick = { onAction(QuickAction.DECREMENT) },
        )
        QuickActionItem(
            labelRes = R.string.product_action_finished,
            icon = Icons.Outlined.TaskAlt,
            onClick = { onAction(QuickAction.FINISHED) },
        )
        QuickActionItem(
            labelRes = R.string.product_action_repeat,
            icon = Icons.Outlined.AddCircleOutline,
            onClick = { onAction(QuickAction.REPEAT) },
        )
        QuickActionItem(
            labelRes = R.string.product_action_shopping,
            icon = Icons.Outlined.ShoppingCart,
            onClick = { onAction(QuickAction.TO_SHOPPING) },
        )
    }
}

@Composable
private fun QuickActionItem(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
