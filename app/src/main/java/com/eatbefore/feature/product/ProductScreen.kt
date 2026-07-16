package com.eatbefore.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MoveUp
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.eatbefore.core.designsystem.component.StatusBadge
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.format.formatDate
import com.eatbefore.core.designsystem.format.formatQuantity
import com.eatbefore.core.designsystem.format.remainingText
import com.eatbefore.feature.history.eventLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    onBack: () -> Unit,
    viewModel: ProductViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    var showQuantityDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

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
            initialName = editableItem.product.name,
            initialBrand = editableItem.product.brand.orEmpty(),
            initialCategory = editableItem.product.category.orEmpty(),
            initialExpiry = editableItem.batch.expirationDate,
            initialNote = editableItem.batch.note.orEmpty(),
            onConfirm = { name, brand, category, expiry, note ->
                viewModel.updateDetails(
                    name = name,
                    brand = brand.ifBlank { null },
                    category = category.ifBlank { null },
                    expirationDate = expiry,
                    note = note.ifBlank { null },
                )
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.item?.product?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
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
                            onDiscard = {
                                showOverflow = false
                                viewModel.discard()
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
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        val item = state.item
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (item != null) {
                ProductHeaderCard(
                    item = item,
                    expiryStatus = state.expiryStatus,
                    remainingDays = state.remainingDays,
                    onEditQuantity = { showQuantityDialog = true },
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

                HistorySection(state.history)
            }
        }
    }
}

/** Photo, name, quantity and status in one block — everything the user checks first. */
@Composable
private fun ProductHeaderCard(
    item: com.eatbefore.domain.model.InventoryItem,
    expiryStatus: com.eatbefore.domain.model.ExpiryStatus,
    remainingDays: Long?,
    onEditQuantity: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Product photo from the catalog (https-only URLs are stored).
            item.product.imageUri?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit,
                )
            }

            item.product.brand?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    formatQuantity(item.batch.quantity, item.batch.measurementUnit),
                    style = MaterialTheme.typography.headlineSmall,
                )
                // An explicit button — the amount used to be a hidden tap target.
                IconButton(onClick = onEditQuantity) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.product_edit_quantity),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                StatusBadge(status = expiryStatus)
            }

            HorizontalDivider()

            DetailRow(stringResource(R.string.product_location), item.location.displayName())
            item.batch.effectiveExpirationDate?.let { date ->
                DetailRow(stringResource(R.string.product_expiration), formatDate(date))
            }
            remainingText(remainingDays)?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            }
            item.batch.openedAt?.let {
                DetailRow(
                    stringResource(R.string.product_opened_at),
                    formatDate(it),
                )
            }
            // Before opening, tell the user what opening will cost them in shelf life.
            if (item.batch.openedAt == null) {
                item.batch.recommendedUseAfterOpeningDays?.let { days ->
                    Text(
                        pluralStringResource(R.plurals.product_after_opening_hint, days, days),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
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

@Composable
private fun HistorySection(history: List<com.eatbefore.domain.model.InventoryEvent>) {
    if (history.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.product_history),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        history.forEach { event ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(eventLabel(event.eventType), style = MaterialTheme.typography.bodyMedium)
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

/** Edits product-card fields and the batch expiry/note in one place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDetailsDialog(
    initialName: String,
    initialBrand: String,
    initialCategory: String,
    initialExpiry: java.time.LocalDate?,
    initialNote: String,
    onConfirm: (
        name: String,
        brand: String,
        category: String,
        expiry: java.time.LocalDate?,
        note: String,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var brand by remember { mutableStateOf(initialBrand) }
    var category by remember { mutableStateOf(initialCategory) }
    var expiry by remember { mutableStateOf(initialExpiry) }
    var note by remember { mutableStateOf(initialNote) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = expiry?.toEpochDay()?.times(86_400_000L),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    expiry = datePickerState.selectedDateMillis
                        ?.let { java.time.LocalDate.ofEpochDay(it / 86_400_000L) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                onClick = { onConfirm(name, brand, category, expiry, note) },
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
