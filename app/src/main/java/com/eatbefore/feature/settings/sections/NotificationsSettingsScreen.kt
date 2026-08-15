package com.eatbefore.feature.settings.sections

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import com.eatbefore.core.designsystem.component.SectionCard
import com.eatbefore.core.designsystem.component.SettingSwitchRow
import com.eatbefore.core.designsystem.component.SettingValueRow
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.feature.settings.QuietHoursRow
import com.eatbefore.feature.settings.SettingsSectionScaffold
import com.eatbefore.feature.settings.SettingsViewModel
import com.eatbefore.feature.settings.formatTime
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NotificationsSettingsScreen(
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
        notifPermission != null &&
        !notifPermission.status.isGranted

    SettingsSectionScaffold(
        titleRes = R.string.settings_section_notifications,
        onBack = onBack,
        viewModel = viewModel,
    ) {
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
                        onStartChange = { viewModel.setQuietHours(true, it, prefs.quietEndHour) },
                        onEndChange = { viewModel.setQuietHours(true, prefs.quietStartHour, it) },
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
