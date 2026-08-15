package com.eatbefore.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.SettingActionRow
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.update.DownloadState
import com.eatbefore.feature.settings.SettingsNote
import com.eatbefore.feature.settings.UpdateViewModel

/**
 * Checking for a new version and installing it, without leaving the app.
 *
 * The app is not in a store, so this is the only thing that can tell the user a fix
 * exists. What it will not do is decide for them: nothing is downloaded until the button
 * is pressed, because the file is around 66 MB.
 */
@Composable
fun UpdateRow(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        SettingActionRow(
            title = stringResource(R.string.update_check),
            subtitle = when {
                state.isChecking -> stringResource(R.string.update_checking)
                state.available != null ->
                    stringResource(R.string.update_available, state.available!!.version.toString())
                else -> stringResource(R.string.update_check_desc)
            },
            onClick = viewModel::check,
        )

        state.messageRes?.let { messageRes ->
            SettingsNote(stringResource(messageRes))
        }

        state.available?.let { update ->
            when (val download = state.download) {
                is DownloadState.Running -> Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                ) {
                    LinearProgressIndicator(
                        progress = { download.percent / PERCENT },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.update_downloading, download.percent),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = viewModel::cancelDownload) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }

                is DownloadState.Failed -> Column {
                    SettingsNote(stringResource(R.string.update_download_failed))
                    Button(onClick = viewModel::download) {
                        Text(stringResource(R.string.update_download))
                    }
                }

                // Ready is a blink: the installer is handed the file the moment it lands.
                is DownloadState.Ready -> SettingsNote(stringResource(R.string.update_installing))

                DownloadState.Idle -> Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                ) {
                    if (update.notes.isNotBlank()) {
                        Text(
                            update.notes.lines().take(NOTES_LINES).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Said before the tap, not after: on mobile data this is real money.
                    SettingsNote(
                        stringResource(R.string.update_size, update.sizeBytes / BYTES_IN_MB),
                        modifier = Modifier.padding(top = Dimens.spaceXs),
                    )
                    Button(onClick = viewModel::download) {
                        Text(stringResource(R.string.update_download))
                    }
                }
            }
        }

        if (state.needsInstallPermission) {
            SettingsNote(stringResource(R.string.update_permission_needed))
            TextButton(onClick = { context.startActivity(viewModel.unknownSourcesIntent()) }) {
                Text(stringResource(R.string.update_permission_open))
            }
        }
    }
}

private const val PERCENT = 100f
private const val BYTES_IN_MB = 1024 * 1024
private const val NOTES_LINES = 6
