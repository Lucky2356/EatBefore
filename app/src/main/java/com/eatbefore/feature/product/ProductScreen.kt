package com.eatbefore.feature.product

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MoveUp
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.AnnouncedSnackbarHost
import com.eatbefore.core.designsystem.component.AppCard
import com.eatbefore.core.designsystem.component.AppTopBar
import com.eatbefore.core.designsystem.component.ExpiryDatePickerDialog
import com.eatbefore.core.designsystem.component.ExpiryLabel
import com.eatbefore.core.designsystem.component.ExpiryOnlyDialog
import com.eatbefore.core.designsystem.component.QuantityStepper
import com.eatbefore.core.designsystem.component.SectionCard
import com.eatbefore.core.designsystem.component.StatusBadge
import com.eatbefore.core.designsystem.component.toVisual
import com.eatbefore.core.designsystem.format.currencySymbol
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.format.formatDate
import com.eatbefore.core.designsystem.format.formatMoney
import com.eatbefore.core.designsystem.format.formatQuantity
import com.eatbefore.core.designsystem.format.remainingText
import com.eatbefore.core.designsystem.format.storageDisplayName
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.Shapes
import com.eatbefore.feature.common.InventoryRowUi
import com.eatbefore.feature.history.eventAuthor
import com.eatbefore.feature.history.eventLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    onBack: () -> Unit,
    onOpenBatch: (Long) -> Unit,
    viewModel: ProductViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    var showQuantityDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }
    // Discarding writes off the whole batch and is easy to hit by accident in a menu.
    var showDiscardConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.closed) {
        if (state.closed) onBack()
    }

    // While the shopping-list offer dialog is up it is the primary affordance, so the undo
    // snackbar waits; keying on the dialog flag too means undo appears as soon as the
    // dialog is answered, instead of never (the action would then only be undoable from
    // History).
    val actionMessage = state.actionMessageRes?.let { stringResource(it) } ?: ""
    LaunchedEffect(state.undoableActionAt, state.offerShoppingList) {
        if (state.undoableActionAt != null && !state.offerShoppingList) {
            val result = snackbarHost.showSnackbar(
                message = actionMessage,
                actionLabel = undoLabel,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undo()
            } else {
                viewModel.consumeUndoSignal()
            }
        }
    }

    val editableItem = state.item
    if (showEditDialog && editableItem != null) {
        EditDetailsDialog(
            initial = EditedDetails(
                name = editableItem.product.name,
                brand = editableItem.product.brand.orEmpty(),
                category = editableItem.product.category.orEmpty(),
                expiry = editableItem.batch.expirationDate,
                note = editableItem.batch.note.orEmpty(),
                price = editableItem.batch.price,
                purchaseDate = editableItem.batch.purchaseDate,
            ),
            currency = editableItem.batch.currency,
            today = viewModel.today,
            onConfirm = { edited ->
                viewModel.updateDetails(
                    name = edited.name,
                    brand = edited.brand.ifBlank { null },
                    category = edited.category.ifBlank { null },
                    expirationDate = edited.expiry,
                    note = edited.note.ifBlank { null },
                    price = edited.price,
                    purchaseDate = edited.purchaseDate,
                )
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }

    if (showRepeatDialog) {
        ExpiryOnlyDialog(
            title = stringResource(R.string.product_repeat_title),
            today = viewModel.today,
            onConfirm = { date ->
                showRepeatDialog = false
                viewModel.repeatPurchase(date)
            },
            onDismiss = { showRepeatDialog = false },
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.product_discard_confirm_title)) },
            text = { Text(stringResource(R.string.product_discard_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    viewModel.discard()
                }) { Text(stringResource(R.string.product_action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showQuantityDialog) {
        val current = state.item?.batch?.quantity ?: 0.0
        QuantityDialog(
            initial = current,
            onConfirm = { value ->
                viewModel.setQuantity(value)
                showQuantityDialog = false
            },
            onDismiss = { showQuantityDialog = false },
        )
    }

    val offeredItem = state.item
    if (state.offerShoppingList && offeredItem != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissShoppingOffer,
            title = { Text(stringResource(R.string.shopping_offer_title)) },
            text = { Text(stringResource(R.string.shopping_offer_body, offeredItem.product.name)) },
            confirmButton = {
                TextButton(onClick = viewModel::acceptShoppingOffer) {
                    Text(stringResource(R.string.shopping_offer_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissShoppingOffer) {
                    Text(stringResource(R.string.shopping_offer_no))
                }
            },
        )
    }

    var showOverflow by remember { mutableStateOf(false) }
    var showMoveMenu by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    // The hero card carries the name in full at the top of the screen; the bar only needs
    // it once that card has gone past. Showing both at once put the same word twice within
    // half a centimetre of itself and read as a bug.
    val titleInBar by remember { derivedStateOf { scrollState.value > 0 } }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                // The name is on the hero card now, in full and at a size worth reading.
                // Keeping it here too means the one place it is clipped to a single line is
                // also the place it is repeated.
                title = state.item?.product?.name.orEmpty(),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                titleContent = {
                    AnimatedVisibility(visible = titleInBar, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            state.item?.product?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (state.item != null) {
                        // Secondary actions live here so the primary three never scroll.
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.product_more_actions),
                            )
                        }
                        ProductOverflowMenu(
                            expanded = showOverflow,
                            onDismiss = { showOverflow = false },
                            onEdit = {
                                showOverflow = false
                                showEditDialog = true
                            },
                            onMove = {
                                showOverflow = false
                                showMoveMenu = true
                            },
                            onRepeat = {
                                showOverflow = false
                                showRepeatDialog = true
                            },
                            onDiscard = {
                                showOverflow = false
                                showDiscardConfirm = true
                            },
                            onToShopping = {
                                showOverflow = false
                                viewModel.addToShopping()
                            },
                        )
                        MoveMenu(
                            expanded = showMoveMenu,
                            state = state,
                            onDismiss = { showMoveMenu = false },
                            onMoveTo = {
                                viewModel.moveTo(it)
                                showMoveMenu = false
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { AnnouncedSnackbarHost(snackbarHost) },
    ) { padding ->
        val item = state.item
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            if (item != null) {
                ProductHeroCard(
                    item = item,
                    expiryStatus = state.expiryStatus,
                    remainingDays = state.remainingDays,
                )

                QuantityCard(
                    item = item,
                    onEditQuantity = { showQuantityDialog = true },
                    onDecreaseQuantity = viewModel::decrement,
                    onIncreaseQuantity = viewModel::increment,
                )

                PrimaryActions(
                    isOpened = item.batch.openedAt != null,
                    onOpen = viewModel::open,
                    // Detailed mode asks for the exact remaining amount instead of −1.
                    onDecrement = {
                        if (state.detailedMode) showQuantityDialog = true else viewModel.decrement()
                    },
                    onFinished = viewModel::markFinished,
                )

                DetailsCard(item = item, remainingDays = state.remainingDays)

                OtherBatchesSection(
                    others = state.otherBatches,
                    total = state.total,
                    onOpenBatch = onOpenBatch,
                )

                HistorySection(state.history, state.peerNames)
            }
        }
    }
}

/**
 * What the product is: its photo, its name, and how long it has left.
 *
 * The name used to live only in the title bar, where it was clipped to one line and sat in
 * the same type as every other screen's title — a card about a specific carton of milk gave
 * no sign of which carton it was about. The photo is the fastest way to confirm it is the
 * right one, so it leads.
 */
@Composable
private fun ProductHeroCard(
    item: com.eatbefore.domain.model.InventoryItem,
    expiryStatus: com.eatbefore.domain.model.ExpiryStatus,
    remainingDays: Long?,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            // Product photo from the catalog (https-only URLs are stored).
            item.product.imageUri?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = Dimens.productPhotoHeight)
                        .clip(Shapes.control),
                    contentScale = ContentScale.Fit,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                item.product.brand?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    item.product.name,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                StatusBadge(status = expiryStatus)
                // Only when it adds something. For a batch due today both the state and the
                // countdown render as the same sentence, and the card said "Expires today"
                // twice in a row, half a centimetre apart. For three days left they differ
                // — "Expiring soon" and "3 days left" — and the number is worth the space.
                val countdown = remainingText(remainingDays)
                if (countdown != null && countdown != stringResource(expiryStatus.toVisual().labelResId)) {
                    Text(
                        countdown,
                        style = MaterialTheme.typography.bodyMedium,
                        // Neutral on purpose: the status beside it already says how urgent
                        // this is, and colouring the same fact twice only invites doubt
                        // about which of the two is right.
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * How much is left, and the two ways to change it.
 *
 * Its own block. It used to share a row with the edit button and the status badge, so
 * "1 pc", a minus, a plus, a pencil and the word "expiring soon" all sat on one line with
 * nothing to say which of them belonged together.
 */
@Composable
private fun QuantityCard(
    item: com.eatbefore.domain.model.InventoryItem,
    onEditQuantity: () -> Unit,
    onDecreaseQuantity: () -> Unit,
    onIncreaseQuantity: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Text(
                stringResource(R.string.product_quantity),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            QuantityStepper(
                text = formatQuantity(item.batch.quantity, item.batch.measurementUnit),
                onDecrease = onDecreaseQuantity,
                onIncrease = onIncreaseQuantity,
                // Reaching zero is "finished", which is its own button with its own
                // follow-up question — the stepper must not trigger it by accident.
                decreaseEnabled = item.batch.quantity > 1.0,
            )
            // An explicit button — the amount used to be a hidden tap target.
            IconButton(onClick = onEditQuantity) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.product_edit_quantity),
                )
            }
        }
    }
}

/** Everything known about this particular batch, as labelled pairs. */
@Composable
private fun DetailsCard(
    item: com.eatbefore.domain.model.InventoryItem,
    remainingDays: Long?,
) {
    SectionCard(title = stringResource(R.string.product_details)) {
        DetailRow(stringResource(R.string.product_location), item.location.displayName())
        item.batch.effectiveExpirationDate?.let { date ->
            DetailRow(stringResource(R.string.product_expiration), formatDate(date))
        }
        remainingText(remainingDays)?.let { text ->
            DetailRow(stringResource(R.string.product_remaining), text)
        }
        item.batch.openedAt?.let {
            DetailRow(stringResource(R.string.product_opened_at), formatDate(it))
        }
        item.batch.purchaseDate?.let { date ->
            DetailRow(stringResource(R.string.product_purchase_date), formatDate(date))
        }
        item.batch.price?.let { price ->
            DetailRow(
                stringResource(R.string.product_price),
                formatMoney(price, item.batch.currency),
            )
        }
        // Before opening, tell the user what opening will cost them in shelf life.
        if (item.batch.openedAt == null) {
            item.batch.recommendedUseAfterOpeningDays?.let { days ->
                Text(
                    pluralStringResource(R.plurals.product_after_opening_hint, days, days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.spaceXs),
                )
            }
        }
    }
}

/** The three actions used daily, always visible and thumb-sized. */
@Composable
private fun PrimaryActions(
    isOpened: Boolean,
    onOpen: () -> Unit,
    onDecrement: () -> Unit,
    onFinished: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        ActionButton(
            icon = Icons.Outlined.LockOpen,
            label = stringResource(R.string.product_action_open),
            onClick = onOpen,
            enabled = !isOpened,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            icon = Icons.Outlined.RemoveCircleOutline,
            label = stringResource(R.string.product_action_decrement),
            onClick = onDecrement,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            icon = Icons.Outlined.TaskAlt,
            label = stringResource(R.string.product_action_finished),
            onClick = onFinished,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 72.dp),
        contentPadding = PaddingValues(vertical = Dimens.spaceMd, horizontal = Dimens.spaceXs),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        ) {
            Icon(icon, contentDescription = null)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ProductOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onRepeat: () -> Unit,
    onDiscard: () -> Unit,
    onToShopping: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.product_action_edit)) },
            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            onClick = onEdit,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.product_action_move)) },
            leadingIcon = { Icon(Icons.Outlined.MoveUp, contentDescription = null) },
            onClick = onMove,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.product_action_repeat)) },
            leadingIcon = { Icon(Icons.Outlined.AddCircleOutline, contentDescription = null) },
            onClick = onRepeat,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.product_action_shopping)) },
            leadingIcon = { Icon(Icons.Outlined.ShoppingCart, contentDescription = null) },
            onClick = onToShopping,
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.product_action_discard)) },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            onClick = onDiscard,
        )
    }
}

@Composable
private fun MoveMenu(
    expanded: Boolean,
    state: ProductUiState,
    onDismiss: () -> Unit,
    onMoveTo: (Long) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        state.locations
            .filter { it.id != state.item?.location?.id }
            .forEach { location ->
                DropdownMenuItem(
                    text = { Text(location.displayName()) },
                    onClick = { onMoveTo(location.id) },
                )
            }
    }
}

/**
 * The other packs of this product still at home.
 *
 * The card only ever showed the one batch that was tapped, so three cartons of milk with
 * three different dates were nowhere visible together — the one thing a user standing at
 * the fridge with a carton in hand actually wants to know. Soonest first, and tapping one
 * switches the card to it.
 */
@Composable
private fun OtherBatchesSection(
    others: List<InventoryRowUi>,
    total: BatchTotal?,
    onOpenBatch: (Long) -> Unit,
) {
    if (others.isEmpty()) return
    SectionCard(title = pluralStringResource(R.plurals.product_other_batches, others.size, others.size)) {
        others.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBatch(row.batchId) }
                    .heightIn(min = Dimens.minTouchTarget)
                    .padding(vertical = Dimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                // An open pack is on a different clock from a sealed one, and choosing
                // which carton to finish is exactly when that matters.
                if (row.isOpened) {
                    Icon(
                        Icons.Outlined.LockOpen,
                        contentDescription = stringResource(R.string.row_opened),
                        modifier = Modifier.size(Dimens.iconSm),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${formatQuantity(row.quantity, row.unit)} · " +
                        storageDisplayName(row.locationName, row.locationType),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ExpiryLabel(status = row.expiryStatus, remainingDays = row.remainingDays)
            }
        }
        if (total != null) {
            HorizontalDivider()
            Text(
                stringResource(R.string.product_total_amount, formatQuantity(total.quantity, total.unit)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Dimens.spaceXs),
            )
        }
    }
}

@Composable
private fun HistorySection(
    history: List<com.eatbefore.domain.model.InventoryEvent>,
    peerNames: Map<String, String>,
) {
    if (history.isEmpty()) return
    // In a card like everything else on this screen. Bare rows on the page made the history
    // the one block here that did not look like it belonged to the product.
    SectionCard(title = stringResource(R.string.product_history)) {
        history.forEach { event ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.spaceXs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val author = eventAuthor(event, peerNames)
                Text(
                    text = if (author == null) {
                        eventLabel(event.eventType)
                    } else {
                        "${eventLabel(event.eventType)} · $author"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    formatDate(event.createdAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuantityDialog(
    initial: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember {
        mutableStateOf(if (initial % 1.0 == 0.0) initial.toLong().toString() else initial.toString())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_edit_quantity)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { text.toDoubleOrNull()?.let(onConfirm) },
                enabled = text.toDoubleOrNull() != null,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Everything the edit dialog can change, so its callback stays readable. */
private data class EditedDetails(
    val name: String,
    val brand: String,
    val category: String,
    val expiry: java.time.LocalDate?,
    val note: String,
    val price: Double?,
    val purchaseDate: java.time.LocalDate?,
)

/** Edits product-card fields and the batch expiry/note/price in one place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDetailsDialog(
    initial: EditedDetails,
    currency: String?,
    today: java.time.LocalDate,
    onConfirm: (EditedDetails) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var brand by remember { mutableStateOf(initial.brand) }
    var category by remember { mutableStateOf(initial.category) }
    var expiry by remember { mutableStateOf(initial.expiry) }
    var note by remember { mutableStateOf(initial.note) }
    var price by remember {
        mutableStateOf(initial.price?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty())
    }
    var purchaseDate by remember { mutableStateOf(initial.purchaseDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showPurchasePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        ExpiryDatePickerDialog(
            initial = expiry,
            onConfirm = { expiry = it },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showPurchasePicker) {
        // A purchase is in the past; the picker starts from today rather than the expiry.
        ExpiryDatePickerDialog(
            initial = purchaseDate ?: today,
            onConfirm = { purchaseDate = it },
            onDismiss = { showPurchasePicker = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.add_name)) },
                    singleLine = true,
                    isError = name.isBlank(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text(stringResource(R.string.add_brand)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.add_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            stringResource(R.string.add_expiration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            expiry?.let { com.eatbefore.core.designsystem.format.formatDate(it) }
                                ?: stringResource(R.string.status_no_date),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Row {
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(stringResource(R.string.product_edit_pick_date))
                        }
                        if (expiry != null) {
                            TextButton(onClick = { expiry = null }) {
                                Text(stringResource(R.string.add_expiry_none))
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            stringResource(R.string.product_purchase_date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            purchaseDate?.let { formatDate(it) } ?: stringResource(R.string.status_no_date),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    TextButton(onClick = { showPurchasePicker = true }) {
                        Text(stringResource(R.string.product_edit_pick_date))
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = price,
                    onValueChange = { value ->
                        price = value.replace(',', '.').filter { it.isDigit() || it == '.' }
                    },
                    label = { Text(stringResource(R.string.add_price, currencySymbol(currency))) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.add_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        EditedDetails(
                            name = name,
                            brand = brand,
                            category = category,
                            expiry = expiry,
                            note = note,
                            price = price.toDoubleOrNull()?.takeIf { it > 0 },
                            purchaseDate = purchaseDate,
                        ),
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
