package com.eatbefore.feature.settings.sections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.SectionCard
import com.eatbefore.core.designsystem.component.SettingActionRow
import com.eatbefore.core.designsystem.format.formatDateTime
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.feature.settings.DeviceNameDialog
import com.eatbefore.feature.settings.SettingsNote
import com.eatbefore.feature.settings.SettingsSectionScaffold
import com.eatbefore.feature.settings.SettingsViewModel
import java.time.Instant

@Composable
fun SharingSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    var showDeviceNameDialog by remember { mutableStateOf(false) }

    val syncFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::enableSharing) }

    SettingsSectionScaffold(
        titleRes = R.string.settings_section_sharing,
        onBack = onBack,
        viewModel = viewModel,
    ) {
        SectionCard(title = stringResource(R.string.settings_section_sharing)) {
            SettingsNote(stringResource(R.string.settings_sharing_desc))
            HorizontalDivider()
            SettingActionRow(
                title = stringResource(R.string.settings_sharing_folder),
                subtitle = if (prefs.syncFolderUri != null) {
                    stringResource(R.string.settings_sharing_folder_set)
                } else {
                    stringResource(R.string.settings_sharing_folder_none)
                },
                onClick = { syncFolderLauncher.launch(null) },
            )
            if (prefs.syncFolderUri != null) {
                HorizontalDivider()
                // Only worth asking once sharing is on: alone, this phone's name has
                // nobody to introduce itself to.
                SettingActionRow(
                    title = stringResource(R.string.settings_device_name),
                    subtitle = prefs.deviceName ?: stringResource(R.string.settings_device_name_desc),
                    onClick = { showDeviceNameDialog = true },
                )
                HorizontalDivider()
                SettingActionRow(
                    title = stringResource(R.string.settings_sharing_now),
                    subtitle = if (prefs.lastSyncAt > 0) {
                        stringResource(
                            R.string.settings_sharing_last,
                            formatDateTime(Instant.ofEpochMilli(prefs.lastSyncAt)),
                        )
                    } else {
                        stringResource(R.string.settings_sharing_never)
                    },
                    onClick = viewModel::syncNow,
                )
                if (isSyncing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                HorizontalDivider()
                SettingActionRow(
                    title = stringResource(R.string.settings_sharing_disable),
                    subtitle = null,
                    onClick = viewModel::disableSharing,
                )
                // Honest about the latency: a cloud folder is not a live connection.
                Text(
                    stringResource(R.string.settings_sharing_delay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.spaceSm, bottom = Dimens.spaceSm),
                )
            }
        }
    }

    if (showDeviceNameDialog) {
        DeviceNameDialog(
            currentName = prefs.deviceName,
            onDismiss = { showDeviceNameDialog = false },
            onSave = { name ->
                viewModel.setDeviceName(name)
                showDeviceNameDialog = false
            },
        )
    }
}
