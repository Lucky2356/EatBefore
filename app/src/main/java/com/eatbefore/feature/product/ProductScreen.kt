package com.eatbefore.feature.product

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoveUp
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.StatusBadge
import com.eatbefore.core.designsystem.format.displayName
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

    LaunchedEffect(state.closed) {
        if (state.closed) onBack()
    }

    // When the shopping-list offer dialog is up it is the primary affordance, so the undo
    // snackbar stays out of the way (undo remains available from History).
    LaunchedEffect(state.undoableActionAt) {
        if (state.undoableActionAt != null && !state.offerShoppingList) {
            val result = snackbarHost.showSnackbar(
                message = "✓",
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.item?.product?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (item != null) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        item.product.brand?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Tapping the amount opens precise editing (detailed mode).
                        Text(
                            formatQuantity(item.batch.quantity, item.batch.measurementUnit),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.clickable { showQuantityDialog = true },
                        )
                    }
                    StatusBadge(status = state.expiryStatus)
                }

                DetailRow(stringResource(R.string.product_location), item.location.displayName())
                item.batch.effectiveExpirationDate?.let { date ->
                    DetailRow(stringResource(R.string.product_expiration), date.toString())
                }
                remainingText(state.remainingDays)?.let { DetailRow(" ", it) }
                item.batch.openedAt?.let {
                    DetailRow(stringResource(R.string.product_opened_at), "✓")
                }

                HorizontalDivider()

                // Quick actions
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = viewModel::open,
                        label = { Text(stringResource(R.string.product_action_open)) },
                        leadingIcon = { Icon(Icons.Outlined.LockOpen, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = viewModel::decrement,
                        label = { Text(stringResource(R.string.product_action_decrement)) },
                        leadingIcon = { Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = viewModel::markFinished,
                        label = { Text(stringResource(R.string.product_action_finished)) },
                        leadingIcon = { Icon(Icons.Outlined.TaskAlt, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = viewModel::discard,
                        label = { Text(stringResource(R.string.product_action_discard)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = viewModel::addToShopping,
                        label = { Text(stringResource(R.string.product_action_shopping)) },
                        leadingIcon = { Icon(Icons.Outlined.ShoppingCart, contentDescription = null) },
                    )
                    MoveChip(state, viewModel)
                }

                HorizontalDivider()

                Text(stringResource(R.string.product_history), style = MaterialTheme.typography.titleMedium)
                state.history.forEach { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(eventLabel(event.eventType), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            event.createdAt.toString().take(10),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveChip(state: ProductUiState, viewModel: ProductViewModel) {
    var open by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { open = true },
        label = { Text(stringResource(R.string.product_action_move)) },
        leadingIcon = { Icon(Icons.Outlined.MoveUp, contentDescription = null) },
    )
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        state.locations
            .filter { it.id != state.item?.location?.id }
            .forEach { location ->
                DropdownMenuItem(
                    text = { Text(location.displayName()) },
                    onClick = {
                        viewModel.moveTo(location.id)
                        open = false
                    },
                )
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

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
