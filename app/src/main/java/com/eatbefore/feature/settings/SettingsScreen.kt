package com.eatbefore.feature.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.BuildConfig
import com.eatbefore.R
import com.eatbefore.core.backup.BackupManager
import com.eatbefore.core.datastore.ThemeMode
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.format.formatDateTime
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLocations: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var showTimePicker by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { pendingImportUri = it } }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::enableAutoBackup) }

    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbarHost.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    // On Android 13+ the notification runtime permission is required to actually show reminders.
    val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }
    val permissionMissing = prefs.notificationsEnabled &&
        notifPermission != null &&
        !notifPermission.status.isGranted

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                Text(
                    stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyLarge,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = prefs.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size,
                            ),
                        ) { Text(themeModeLabel(mode)) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_dynamic_colors),
                        subtitle = stringResource(R.string.settings_dynamic_colors_desc),
                        checked = prefs.dynamicColors,
                        onCheckedChange = viewModel::setDynamicColors,
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_section_inventory)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.settings_soon_days))
                        Text(
                            stringResource(R.string.settings_soon_days_value, prefs.soonThresholdDays),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = prefs.soonThresholdDays.toFloat(),
                        onValueChange = { viewModel.setSoonDays(it.toInt()) },
                        valueRange = 1f..14f,
                        steps = 12,
                    )
                }
                HorizontalDivider()
                ClickableRow(
                    title = stringResource(R.string.settings_default_location),
                    value = locations.firstOrNull { it.isDefault }?.displayName() ?: "—",
                    onClick = { showLocationPicker = true },
                )
                HorizontalDivider()
                SettingActionRow(
                    title = stringResource(R.string.locations_title),
                    subtitle = stringResource(R.string.locations_manage_desc),
                    onClick = onOpenLocations,
                )
                HorizontalDivider()
                SettingSwitchRow(
                    title = stringResource(R.string.settings_detailed_quantity),
                    subtitle = stringResource(R.string.settings_detailed_quantity_desc),
                    checked = prefs.detailedQuantityMode,
                    onCheckedChange = viewModel::setDetailedQuantityMode,
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_notifications)) {
                SettingSwitchRow(
                    title = stringResource(R.string.settings_notifications_enabled),
                    subtitle = stringResource(R.string.settings_notifications_desc),
                    checked = prefs.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setNotificationsEnabled(enabled)
                        if (enabled) notifPermission?.launchPermissionRequest()
                    },
                )

                if (permissionMissing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_permission_needed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { notifPermission?.launchPermissionRequest() }) {
                            Text(stringResource(R.string.settings_grant_permission))
                        }
                    }
                }

                if (prefs.notificationsEnabled) {
                    HorizontalDivider()
                    ClickableRow(
                        title = stringResource(R.string.settings_notification_time),
                        value = formatTime(prefs.notificationHour, prefs.notificationMinute),
                        onClick = { showTimePicker = true },
                    )
                    HorizontalDivider()
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_quiet_hours),
                        subtitle = stringResource(R.string.settings_quiet_hours_desc),
                        checked = prefs.quietHoursEnabled,
                        onCheckedChange = {
                            viewModel.setQuietHours(it, prefs.quietStartHour, prefs.quietEndHour)
                        },
                    )
                    if (prefs.quietHoursEnabled) {
                        QuietHoursRow(
                            startHour = prefs.quietStartHour,
                            endHour = prefs.quietEndHour,
                            onStartChange = {
                                viewModel.setQuietHours(true, it, prefs.quietEndHour)
                            },
                            onEndChange = {
                                viewModel.setQuietHours(true, prefs.quietStartHour, it)
                            },
                        )
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.settings_section_data)) {
                SettingSwitchRow(
                    title = stringResource(R.string.settings_auto_backup),
                    subtitle = if (prefs.lastAutoBackupAt > 0) {
                        stringResource(
                            R.string.settings_auto_backup_last,
                            formatDateTime(java.time.Instant.ofEpochMilli(prefs.lastAutoBackupAt)),
                        )
                    } else {
                        stringResource(R.string.settings_auto_backup_desc)
                    },
                    checked = prefs.autoBackupEnabled,
                    onCheckedChange = { enabled ->
                        // Enabling needs a folder the app may keep writing to.
                        if (enabled) folderLauncher.launch(null) else viewModel.disableAutoBackup()
                    },
                )
                HorizontalDivider()
                SettingActionRow(
                    title = stringResource(R.string.settings_export),
                    subtitle = stringResource(R.string.settings_export_desc),
                    onClick = { exportLauncher.launch("eatbefore-backup.json") },
                )
                HorizontalDivider()
                SettingActionRow(
                    title = stringResource(R.string.settings_import),
                    subtitle = stringResource(R.string.settings_import_desc),
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_version))
                    Text(
                        BuildConfig.VERSION_NAME,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.settings_about_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showLocationPicker) {
        AlertDialog(
            onDismissRequest = { showLocationPicker = false },
            title = { Text(stringResource(R.string.settings_default_location)) },
            text = {
                Column {
                    locations.forEach { location ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = location.isDefault,
                                    onClick = {
                                        viewModel.setDefaultLocation(location.id)
                                        showLocationPicker = false
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = location.isDefault, onClick = null)
                            Text(
                                location.displayName(),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Importing rewrites user data — always ask, and let them add instead of replace.
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_import_confirm_body))
                    Text(
                        stringResource(R.string.backup_import_merge_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.backup_import_safety_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importFrom(uri, BackupManager.ImportMode.MERGE)
                    pendingImportUri = null
                }) { Text(stringResource(R.string.backup_import_merge)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.importFrom(uri, BackupManager.ImportMode.REPLACE)
                    pendingImportUri = null
                }) { Text(stringResource(R.string.backup_import_confirm_yes)) }
            },
        )
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = prefs.notificationHour,
            initialMinute = prefs.notificationMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setNotificationTime(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

/** A titled tonal card grouping related settings rows. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClickableRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun QuietHoursRow(
    startHour: Int,
    endHour: Int,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_quiet_from))
        HourStepper(hour = startHour, onChange = onStartChange)
        Text(stringResource(R.string.settings_quiet_to))
        HourStepper(hour = endHour, onChange = onEndChange)
    }
}

@Composable
private fun HourStepper(hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange((hour + 23) % 24) }) { Text("−") }
        Text(formatTime(hour, 0), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { onChange((hour + 1) % 24) }) { Text("+") }
    }
}

private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)
