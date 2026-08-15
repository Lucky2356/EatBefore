package com.eatbefore.feature.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.AppCard
import com.eatbefore.core.designsystem.component.ScreenScaffold
import com.eatbefore.core.designsystem.component.animatedItem
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.Shapes

/**
 * The catalogue of product cards: what the app offers when adding, and the only place a
 * card that should never have been there can be struck off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    onBack: () -> Unit,
    viewModel: ProductsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val queryText by viewModel.queryText.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirming by remember { mutableStateOf<ProductRowUi?>(null) }

    val undoLabel = stringResource(R.string.action_undo)
    // Resolved here rather than inside the effect: a string resource can only be read
    // while composing, and the count-carrying messages need one to be formatted at all.
    val messageText = message?.let {
        if (it.count > 0) stringResource(it.textRes, it.count) else stringResource(it.textRes)
    }
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = messageText.orEmpty(),
            actionLabel = if (current.undoProductId != null) undoLabel else null,
        )
        if (result == SnackbarResult.ActionPerformed && current.undoProductId != null) {
            viewModel.undoDelete(current.undoProductId)
        } else {
            viewModel.consumeMessage()
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.products_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = queryText,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (queryText.isNotBlank()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.inventory_search_clear),
                            )
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.products_search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceSm),
            )

            when {
                state.isLoading -> Unit
                state.isEmpty -> EmptyNote(stringResource(R.string.products_empty))
                state.rows.isEmpty() -> EmptyNote(stringResource(R.string.products_nothing_found))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Dimens.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        ProductCard(
                            modifier = animatedItem(),
                            row = row,
                            canDelete = viewModel.canDelete(row),
                            onDelete = { confirming = row },
                        )
                    }
                }
            }
        }
    }

    confirming?.let { row ->
        DeleteDialog(
            row = row,
            onConfirm = {
                viewModel.delete(row)
                confirming = null
            },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(Dimens.spaceLg),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProductCard(
    row: ProductRowUi,
    canDelete: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.fillMaxWidth(), shape = Shapes.row) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium)
                val details = listOfNotNull(row.brand, row.barcode).joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Said plainly on the row, so the disabled button is never a mystery.
                if (row.presentBatches > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.products_in_stock,
                            row.presentBatches,
                            row.presentBatches,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.products_delete),
                )
            }
        }
    }
}

@Composable
private fun DeleteDialog(row: ProductRowUi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.products_delete_title, row.name)) },
        text = { Text(stringResource(R.string.products_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.products_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
