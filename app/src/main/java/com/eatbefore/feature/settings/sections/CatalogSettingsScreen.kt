package com.eatbefore.feature.settings.sections

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
import com.eatbefore.feature.settings.OffAccountDialog
import com.eatbefore.feature.settings.SettingsNote
import com.eatbefore.feature.settings.SettingsSectionScaffold
import com.eatbefore.feature.settings.SettingsViewModel

@Composable
fun CatalogSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    val catalogAccountUsable by viewModel.catalogAccountUsable.collectAsStateWithLifecycle()
    val isCheckingCatalog by viewModel.isCheckingCatalog.collectAsStateWithLifecycle()
    var showOffAccountDialog by remember { mutableStateOf(false) }

    SettingsSectionScaffold(
        titleRes = R.string.settings_section_catalog,
        onBack = onBack,
        viewModel = viewModel,
    ) {
        SectionCard(title = stringResource(R.string.settings_section_catalog)) {
            SettingsNote(stringResource(R.string.settings_catalog_desc))
            HorizontalDivider()
            SettingActionRow(
                title = stringResource(R.string.settings_off_account),
                // The username alone is not proof the account works: the password is
                // encrypted with a key that dies with the app installation, and saying
                // "linked" when it can no longer be read is how a silently useless
                // account looked perfectly healthy.
                subtitle = prefs.offUsername.let { username ->
                    when {
                        username == null -> stringResource(R.string.settings_off_account_none)
                        !catalogAccountUsable ->
                            stringResource(R.string.settings_off_account_needs_password)
                        else -> stringResource(R.string.settings_off_account_linked, username)
                    }
                },
                onClick = { showOffAccountDialog = true },
            )
            if (prefs.offUsername != null) {
                HorizontalDivider()
                SettingActionRow(
                    title = stringResource(R.string.settings_off_check),
                    subtitle = if (isCheckingCatalog) {
                        stringResource(R.string.settings_off_check_running)
                    } else {
                        stringResource(R.string.settings_off_check_desc)
                    },
                    onClick = viewModel::checkCatalogAccount,
                )
            }
        }
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
