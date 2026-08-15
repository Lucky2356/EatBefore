package com.eatbefore.feature.settings.sections

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.BuildConfig
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.SectionCard
import com.eatbefore.core.designsystem.component.SettingActionRow
import com.eatbefore.core.designsystem.component.SettingSwitchRow
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.feature.settings.SettingsNote
import com.eatbefore.feature.settings.SettingsSectionScaffold
import com.eatbefore.feature.settings.SettingsViewModel

@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    var diagnosticsReport by remember { mutableStateOf<String?>(null) }

    SettingsSectionScaffold(
        titleRes = R.string.settings_section_about,
        onBack = onBack,
        viewModel = viewModel,
    ) {
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
            SettingsNote(stringResource(R.string.settings_about_privacy))
            HorizontalDivider()
            UpdateRow()
            SettingSwitchRow(
                title = stringResource(R.string.settings_update_auto),
                subtitle = stringResource(R.string.settings_update_auto_desc),
                checked = prefs.updateCheckEnabled,
                onCheckedChange = viewModel::setUpdateCheckEnabled,
            )
            HorizontalDivider()
            SettingActionRow(
                title = stringResource(R.string.settings_diagnostics),
                subtitle = stringResource(R.string.settings_diagnostics_hint),
                onClick = {
                    // Read at the moment of tapping: the log only changes on failures.
                    val report = viewModel.diagnosticsReport()
                    if (report == null) {
                        viewModel.showDiagnosticsEmpty()
                    } else {
                        diagnosticsReport = report
                    }
                },
            )
        }
    }

    diagnosticsReport?.let { report ->
        AlertDialog(
            onDismissRequest = { diagnosticsReport = null },
            title = { Text(stringResource(R.string.settings_diagnostics)) },
            text = {
                Column {
                    SettingsNote(stringResource(R.string.settings_diagnostics_explain))
                    // The user is entitled to read what they would be sending before
                    // sending it; a report shared blind is a report shared unknowingly.
                    Text(
                        report,
                        modifier = Modifier
                            .padding(top = Dimens.spaceSm)
                            .heightIn(max = Dimens.diagnosticsPreviewHeight)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                    diagnosticsReport = null
                }) {
                    Text(stringResource(R.string.settings_diagnostics_share))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.clearDiagnostics()
                    diagnosticsReport = null
                }) {
                    Text(stringResource(R.string.settings_diagnostics_clear))
                }
            },
        )
    }
}
