package com.eatbefore.feature.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TakeoutDining
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material3.Checkbox
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
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.AppCard
import com.eatbefore.core.designsystem.component.ExpiryLabel
import com.eatbefore.core.designsystem.format.formatQuantity
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.Motion
import com.eatbefore.core.designsystem.theme.Shapes
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
@Composable
fun InventoryRowCard(
    row: InventoryRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onQuickAction: ((QuickAction) -> Unit)? = null,
    showLocation: Boolean = true,
    /**
     * Off where the row sits under a heading that already says the same thing — see
     * [com.eatbefore.feature.common.headingSaysItAll]. The label is wide, and on a phone
     * it was taking the width the product's own place needed.
     */
    showExpiry: Boolean = true,
    selected: Boolean? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // Crossing between the plain and the ticked state rather than swapping: entering
    // selection mode recolours every visible row at once, and twenty simultaneous cuts read
    // as a glitch.
    val container by animateColorAsState(
        targetValue = if (selected == true) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = Motion.quick(),
        label = "rowContainer",
    )

    AppCard(
        modifier = modifier.fillMaxWidth(),
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
        shape = Shapes.row,
        containerColor = container,
    ) {
        Row(
            modifier = Modifier.padding(Dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            // In selection mode the tick takes the thumbnail's place rather than sitting
            // beside it: the row keeps its height, and what a tap does now is unmistakable.
            if (selected != null) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            Box(
                modifier = Modifier
                    .size(Dimens.thumbnailSize)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, Shapes.control),
                contentAlignment = Alignment.Center,
            ) {
                if (row.imageUri != null) {
                    // Catalog photo thumbnail; the location icon is the fallback.
                    coil.compose.AsyncImage(
                        model = row.imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(Dimens.thumbnailSize)
                            .clip(Shapes.control),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = row.locationType.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            modifier = Modifier.size(Dimens.iconSm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val amount = formatQuantity(row.quantity, row.unit)
                    val location = com.eatbefore.core.designsystem.format.storageDisplayName(
                        row.locationName,
                        row.locationType,
                    )
                    Text(
                        // The place is dropped when the list is already grouped by it —
                        // repeating the group heading on every one of its rows is noise.
                        text = if (showLocation) "$amount · $location" else amount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showExpiry) {
                ExpiryLabel(status = row.expiryStatus, remainingDays = row.remainingDays)
            }

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
            labelRes = R.string.inventory_select,
            icon = Icons.Outlined.Checklist,
            onClick = { onAction(QuickAction.SELECT) },
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
