package com.eatbefore.feature.locations

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.AppCard
import com.eatbefore.core.designsystem.component.ScreenScaffold
import com.eatbefore.core.designsystem.component.animatedItem
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.format.label
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.Shapes
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType

/** Settings sub-screen: add, rename, archive storage locations, pick the default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    onBack: () -> Unit,
    viewModel: LocationsViewModel = hiltViewModel(),
) {
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<StorageLocation?>(null) }

    ScreenScaffold(
        title = stringResource(R.string.locations_title),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.locations_add))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            items(locations, key = { it.id }) { location ->
                LocationCard(
                    modifier = animatedItem(),
                    location = location,
                    canArchive = viewModel.canArchive(location),
                    onSetDefault = { viewModel.setDefault(location.id) },
                    onRename = { renaming = location },
                    onArchive = { viewModel.archive(location) },
                )
            }
        }
    }

    if (showAddDialog) {
        AddLocationDialog(
            onConfirm = { name, type ->
                viewModel.add(name, type)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    renaming?.let { location ->
        RenameDialog(
            initial = location.name,
            onConfirm = { newName ->
                viewModel.rename(location, newName)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun LocationCard(
    location: StorageLocation,
    canArchive: Boolean,
    onSetDefault: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(modifier = modifier.fillMaxWidth(), shape = Shapes.row) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val title = location.displayName()
                Text(title, style = MaterialTheme.typography.titleMedium)
                // Preset places are named after their type, so repeating it says nothing;
                // only a custom name benefits from the type as a subtitle.
                val subtitle = when {
                    location.isDefault -> stringResource(R.string.locations_default_badge)
                    location.type.label() != title -> location.type.label()
                    else -> null
                }
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (location.isDefault) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.locations_actions),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (!location.isDefault) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.locations_make_default)) },
                        onClick = {
                            onSetDefault()
                            menuOpen = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.locations_rename)) },
                    onClick = {
                        onRename()
                        menuOpen = false
                    },
                )
                if (canArchive) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.locations_archive)) },
                        onClick = {
                            onArchive()
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddLocationDialog(
    onConfirm: (name: String, type: StorageType) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(StorageType.OTHER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.locations_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.locations_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                ) {
                    StorageType.entries.forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text(candidate.label()) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, type) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.locations_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
