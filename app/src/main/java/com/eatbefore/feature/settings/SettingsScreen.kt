package com.eatbefore.feature.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.BuildConfig
import com.eatbefore.R
import com.eatbefore.core.backup.BackupManager
import com.eatbefore.core.datastore.ThemeMode
import com.eatbefore.core.designsystem.component.ScreenScaffold
import com.eatbefore.core.designsystem.component.SectionCard
import com.eatbefore.core.designsystem.component.SettingActionRow
import com.eatbefore.core.designsystem.component.SettingSwitchRow
import com.eatbefore.core.designsystem.component.SettingValueRow
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.format.formatDateTime
import com.eatbefore.core.designsystem.theme.Dimens
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
    var showOffAccountDialog by remember { mutableStateOf(false) }

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

    ScreenScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        snackbarHostState = snackbarHost,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            SectionCard(title = stringResource(R.string.settings_section_appearance)) {
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

            SectionCard(title = stringResource(R.string.settings_section_inventory)) {
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
                SettingValueRow(
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

            SectionCard(title = stringResource(R.string.settings_section_notifications)) {
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.spaceXs),
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
                    SettingValueRow(
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

            SectionCard(title = stringResource(R.string.settings_section_data)) {
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

            SectionCard(title = stringResource(R.string.settings_section_catalog)) {
                Text(
                    stringResource(R.string.settings_catalog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                SettingActionRow(
                    title = stringResource(R.string.settings_off_account),
                    subtitle = prefs.offUsername
                        ?.let { stringResource(R.string.settings_off_account_linked, it) }
                        ?: stringResource(R.string.settings_off_account_none),
                    onClick = { showOffAccountDialog = true },
                )
            }

            SectionCard(title = stringResource(R.string.settings_section_about)) {
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
                                .padding(vertical = Dimens.spaceSm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = location.isDefault, onClick = null)
                            Text(
                                location.displayName(),
                                modifier = Modifier.padding(start = Dimens.spaceSm),
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
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
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

    if (showOffAccountDialog) {
        OffAccountDialog(
            currentUsername = prefs.offUsername,
            onDismiss = { showOffAccountDialog = false },
            onSave = { user, pass ->
                viewModel.setOffAccount(user, pass)
                showOffAccountDialog = false
            },
        )
    }
}

/**
 * Links an Open Food Facts account so unknown products can be contributed back.
 *
 * The password is stored encrypted (Android Keystore) and is never shown again — the
 * field starts empty on reopen, and leaving it empty keeps the saved one.
 */
@Composable
private fun OffAccountDialog(
    currentUsername: String?,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var username by remember { mutableStateOf(currentUsername.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_off_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                Text(
                    stringResource(R.string.settings_off_account_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.settings_off_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.settings_off_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    if (passwordVisible) {
                                        R.string.settings_off_password_hide
                                    } else {
                                        R.string.settings_off_password_show
                                    },
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(username, password.takeIf { it.isNotEmpty() }) },
                // A new link needs both fields; an existing one may keep its password.
                enabled = username.isNotBlank() && (password.isNotEmpty() || currentUsername != null),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (currentUsername != null) {
                    TextButton(onClick = { onSave("", null) }) {
                        Text(stringResource(R.string.settings_off_account_unlink))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

@Composable
private fun QuietHoursRow(
    startHour: Int,
    endHour: Int,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.spaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
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
