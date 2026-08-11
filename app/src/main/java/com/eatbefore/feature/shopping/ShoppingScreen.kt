package com.eatbefore.feature.shopping

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAddCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.EmptyState
import com.eatbefore.core.designsystem.component.ScreenScaffold
import com.eatbefore.core.designsystem.format.formatQuantity
import com.eatbefore.core.designsystem.format.shortLabel
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.domain.model.MeasurementUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editedRow by remember { mutableStateOf<ShoppingRowUi?>(null) }

    val messageText = message?.let { stringResource(it.textRes) }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(message) {
        val current = message
        if (current != null && messageText != null) {
            val result = snackbarHost.showSnackbar(
                message = messageText,
                actionLabel = if (current.undoable) undoLabel else null,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
            viewModel.consumeMessage()
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.shopping_title),
        snackbarHostState = snackbarHost,
        actions = {
            if (state.groups.isNotEmpty()) {
                val shareTitle = stringResource(R.string.shopping_share_title)
                val unitLabels = MeasurementUnit.entries.associateWith { it.shortLabel() }
                IconButton(onClick = {
                    val text = viewModel.buildShareText(shareTitle) { unitLabels.getValue(it) }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.shopping_share),
                    )
                }
            }
            // Only worth offering once something has been bought; an always-present button
            // that usually does nothing teaches people to ignore it.
            if (state.hasCompleted) {
                IconButton(onClick = viewModel::clearCompleted) {
                    Icon(
                        Icons.AutoMirrored.Outlined.PlaylistAddCheck,
                        contentDescription = stringResource(R.string.shopping_clear_completed),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shopping_add))
            }
        },
    ) { padding ->
        val isEmpty = state.groups.isEmpty()
        if (!state.isLoading && isEmpty && state.frequent.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.shopping_empty),
                actionLabel = stringResource(R.string.shopping_add),
                onAction = { showAddDialog = true },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.spaceLg),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                if (state.frequent.isNotEmpty()) {
                    item(key = "frequent") {
                        FrequentSection(
                            frequent = state.frequent,
                            onAdd = viewModel::addFrequent,
                        )
                    }
                }

                if (isEmpty) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.shopping_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Dimens.spaceXl),
                        )
                    }
                }

                state.groups.forEach { (category, rows) ->
                    item(key = "header-${category ?: "none"}") {
                        Text(
                            text = category ?: stringResource(R.string.shopping_no_category),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Dimens.spaceMd, bottom = Dimens.spaceXs),
                        )
                    }
                    items(rows, key = { it.id }) { row ->
                        ShoppingRow(
                            row = row,
                            onToggle = { viewModel.toggle(row.id) },
                            onEdit = { editedRow = row },
                            onMoveToStock = { viewModel.moveToStock(row.id) },
                            onDelete = { viewModel.delete(row.id) },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ShoppingItemDialog(
            title = stringResource(R.string.shopping_add),
            confirmLabel = stringResource(R.string.action_add),
            edited = null,
            onConfirm = { name, quantity, unit ->
                viewModel.addManual(name, quantity, unit)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    editedRow?.let { row ->
        ShoppingItemDialog(
            title = stringResource(R.string.shopping_edit),
            confirmLabel = stringResource(R.string.action_save),
            edited = row,
            onConfirm = { name, quantity, unit ->
                viewModel.updateItem(row.id, name, quantity, unit)
                editedRow = null
            },
            onDismiss = { editedRow = null },
        )
    }
}

@Composable
private fun ShoppingRow(
    row: ShoppingRowUi,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onMoveToStock: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.isCompleted, onCheckedChange = { onToggle() })
        // The text itself opens the editor: the checkbox and the two buttons already own
        // the rest of the row, and a fourth icon would leave nothing for the name.
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (row.isCompleted) TextDecoration.LineThrough else null,
            )
            Text(
                text = formatQuantity(row.quantity, row.unit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Buying it puts it straight back into stock — the fast path after shopping.
        IconButton(onClick = onMoveToStock) {
            Icon(
                Icons.Outlined.Inventory2,
                contentDescription = stringResource(R.string.shopping_to_inventory),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.shopping_delete))
        }
    }
}

/**
 * Regular purchases as one-tap chips. The data already exists in history — this just
 * saves the user from retyping "молоко" every week.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrequentSection(
    frequent: List<FrequentProductUi>,
    onAdd: (Long) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = Dimens.spaceSm)) {
        Text(
            stringResource(R.string.shopping_frequent),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Dimens.spaceSm),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
            frequent.forEach { product ->
                AssistChip(
                    onClick = { onAdd(product.productId) },
                    label = { Text(product.name, maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ShoppingItemDialog(
    title: String,
    confirmLabel: String,
    /** The row being corrected, or null when adding a new one. */
    edited: ShoppingRowUi?,
    onConfirm: (String, Double, MeasurementUnit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(edited?.title.orEmpty()) }
    var quantity by remember {
        val initial = edited?.quantity ?: 1.0
        mutableStateOf(if (initial % 1.0 == 0.0) initial.toLong().toString() else initial.toString())
    }
    var unit by remember { mutableStateOf(edited?.unit ?: MeasurementUnit.PIECE) }
    // A row that came from the inventory carries the product's own name.
    val nameEditable = edited == null || edited.isCustom

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.shopping_item_name)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    ),
                    singleLine = true,
                    enabled = nameEditable,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.shopping_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Without this everything was a "piece", so "2 кг картошки" was unwritable.
                Text(
                    stringResource(R.string.shopping_unit),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                ) {
                    MeasurementUnit.entries.forEach { entry ->
                        FilterChip(
                            selected = unit == entry,
                            onClick = { unit = entry },
                            label = { Text(entry.shortLabel()) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), quantity.toDoubleOrNull() ?: 1.0, unit) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
