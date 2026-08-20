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
import androidx.compose.ui.platform.LocalDensity
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
 * What a row leaves out, or says more briefly.
 *
 * Every flag here answers the same question — what did the heading above this row already
 * say? — so they travel together rather than as three more arguments on a call that the
 * complexity budget had already run out of room for.
 */
data class RowDisplay(
    /** Off when one place is already chosen, and the chip above the list names it. */
    val showLocation: Boolean = true,
    /** Off under a heading that says the whole of it — see [headingSaysItAll]. */
    val showExpiry: Boolean = true,
    /** See [com.eatbefore.core.designsystem.component.ExpiryLabel]'s own parameter. */
    val conciseExpiry: Boolean = false,
)

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
    display: RowDisplay = RowDisplay(),
    selected: Boolean? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // At the largest system font sizes the expiry label and the product's name cannot
    // both fit on one line, and the label wins: it is measured before the name's weighted
    // column, so at a scale of 2.0 the row showed a full-width red slab and no name at
    // all. Above 1.5 the label moves under the name instead of beside it.
    val stackExpiry = LocalDensity.current.fontScale >= STACK_EXPIRY_FONT_SCALE

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
                        text = if (display.showLocation) "$amount · $location" else amount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (display.showExpiry && stackExpiry) {
                    ExpiryLabel(
                        status = row.expiryStatus,
                        remainingDays = row.remainingDays,
                        modifier = Modifier.padding(top = Dimens.spaceXs),
                        concise = display.conciseExpiry,
                    )
                }
            }
            if (display.showExpiry && !stackExpiry) {
                ExpiryLabel(
                    status = row.expiryStatus,
                    remainingDays = row.remainingDays,
                    concise = display.conciseExpiry,
                )
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

/**
 * System font scale at and above which the expiry label stops sharing a line with the
 * product's name.
 *
 * Set from what the device actually does, not from taste. The label is measured before
 * the name's weighted column and takes whatever it wants, so the name gets the remainder:
 * at 1.3 that remainder was wide enough for an ellipsis and nothing else, and at 2.0 the
 * row was a full-width red slab with no name on it at all. Anything above the default
 * therefore stops trying to fit both on one line.
 */
private const val STACK_EXPIRY_FONT_SCALE = 1.2f
