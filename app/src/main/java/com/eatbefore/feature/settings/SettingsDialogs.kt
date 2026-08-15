package com.eatbefore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.eatbefore.R
import com.eatbefore.core.backup.AutoBackupEntry
import com.eatbefore.core.backup.BackupManager
import com.eatbefore.core.designsystem.component.SettingActionRow
import com.eatbefore.core.designsystem.format.formatDateTime
import com.eatbefore.core.designsystem.theme.Dimens
import java.time.Instant

/**
 * The automatic copies, newest first, with the moment each was taken.
 *
 * Deliberately plain: this dialog is read when something has gone wrong, and the only
 * decision worth offering is which day to go back to.
 */
@Composable
fun AutoBackupListDialog(
    entries: List<AutoBackupEntry>,
    onDismiss: () -> Unit,
    onPick: (AutoBackupEntry) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_restore_auto)) },
        text = {
            if (entries.isEmpty()) {
                // The folder may be there while the first backup has not run yet, or the
                // permission may have been revoked — either way there is nothing to offer.
                Text(stringResource(R.string.settings_restore_auto_empty))
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                ) {
                    entries.forEach { entry ->
                        SettingActionRow(
                            title = formatDateTime(Instant.ofEpochMilli(entry.writtenAtEpochMillis)),
                            subtitle = entry.name,
                            onClick = { onPick(entry) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Importing rewrites user data — always ask, and let them add instead of replace. */
@Composable
fun ImportConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: (BackupManager.ImportMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = { onConfirm(BackupManager.ImportMode.MERGE) }) {
                Text(stringResource(R.string.backup_import_merge))
            }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(BackupManager.ImportMode.REPLACE) }) {
                Text(stringResource(R.string.backup_import_confirm_yes))
            }
        },
    )
}

/**
 * Names this phone for the other household member.
 *
 * Clearing the field is a real answer, not a cancel: it puts the phone's model back, which
 * is what an unnamed device publishes.
 */
@Composable
fun DeviceNameDialog(
    currentName: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_device_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                Text(
                    stringResource(R.string.settings_device_name_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.settings_device_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Links an Open Food Facts account so unknown products can be contributed back.
 *
 * The password is stored encrypted (Android Keystore) and is never shown again — the
 * field starts empty on reopen, and leaving it empty keeps the saved one.
 */
@Composable
fun OffAccountDialog(
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

/** Shared by the notification section: two hour steppers side by side. */
@Composable
fun QuietHoursRow(
    startHour: Int,
    endHour: Int,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.spaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_quiet_from))
        HourStepper(hour = startHour, onChange = onStartChange)
        Text(stringResource(R.string.settings_quiet_to))
        HourStepper(hour = endHour, onChange = onEndChange)
    }
}

@Composable
private fun HourStepper(hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        TextButton(onClick = { onChange((hour + HOURS_IN_DAY - 1) % HOURS_IN_DAY) }) { Text("−") }
        Text(formatTime(hour, 0), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { onChange((hour + 1) % HOURS_IN_DAY) }) { Text("+") }
    }
}

fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

private const val HOURS_IN_DAY = 24
