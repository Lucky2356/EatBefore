package com.eatbefore.feature.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import com.eatbefore.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }

    // On Android 13+ the notification runtime permission is required to actually show reminders.
    val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }
    val permissionMissing = prefs.notificationsEnabled &&
        notifPermission != null && !notifPermission.status.isGranted

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.settings_section_notifications),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

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

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.settings_soon_days))
                        Text(stringResource(R.string.settings_soon_days_value, prefs.soonThresholdDays))
                    }
                    Slider(
                        value = prefs.soonThresholdDays.toFloat(),
                        onValueChange = { viewModel.setSoonDays(it.toInt()) },
                        valueRange = 1f..14f,
                        steps = 12,
                    )
                }

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
private fun ClickableRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
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

private fun formatTime(hour: Int, minute: Int): String =
    "%02d:%02d".format(hour, minute)
