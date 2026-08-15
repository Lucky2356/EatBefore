package com.eatbefore.feature.settings.sections

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.SectionCard
import com.eatbefore.core.designsystem.component.SettingActionRow
import com.eatbefore.core.designsystem.component.SettingSwitchRow
import com.eatbefore.core.designsystem.format.formatDateTime
import com.eatbefore.feature.settings.AutoBackupListDialog
import com.eatbefore.feature.settings.ImportConfirmDialog
import com.eatbefore.feature.settings.SettingsSectionScaffold
import com.eatbefore.feature.settings.SettingsViewModel
import java.time.Instant

@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    val autoBackups by viewModel.autoBackups.collectAsStateWithLifecycle()
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

    SettingsSectionScaffold(
        titleRes = R.string.settings_section_data,
        onBack = onBack,
        viewModel = viewModel,
    ) {
        SectionCard(title = stringResource(R.string.settings_section_data)) {
            SettingSwitchRow(
                title = stringResource(R.string.settings_auto_backup),
                subtitle = if (prefs.lastAutoBackupAt > 0) {
                    stringResource(
                        R.string.settings_auto_backup_last,
                        formatDateTime(Instant.ofEpochMilli(prefs.lastAutoBackupAt)),
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
            // Only when there is a folder to read: without one there are no automatic
            // copies, and a row that always opens an empty list is a dead end.
            if (prefs.autoBackupFolderUri != null) {
                HorizontalDivider()
                SettingActionRow(
                    title = stringResource(R.string.settings_restore_auto),
                    subtitle = stringResource(R.string.settings_restore_auto_desc),
                    onClick = viewModel::loadAutoBackups,
                )
            }
        }
    }

    autoBackups?.let { entries ->
        AutoBackupListDialog(
            entries = entries,
            onDismiss = viewModel::clearAutoBackups,
            onPick = { entry ->
                viewModel.clearAutoBackups()
                // Straight into the same confirmation the file picker leads to: this is
                // the same destructive import, and it deserves the same question.
                pendingImportUri = entry.uri
            },
        )
    }

    pendingImportUri?.let { uri ->
        ImportConfirmDialog(
            onDismiss = { pendingImportUri = null },
            onConfirm = { mode ->
                viewModel.importFrom(uri, mode)
                pendingImportUri = null
            },
        )
    }
}
