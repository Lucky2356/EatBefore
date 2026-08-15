package com.eatbefore.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.eatbefore.core.designsystem.component.SettingActionRow
import com.eatbefore.core.designsystem.component.SettingSwitchRow
import com.eatbefore.core.designsystem.component.SettingValueRow
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.feature.settings.SettingsSectionScaffold
import com.eatbefore.feature.settings.SettingsViewModel

@Composable
fun InventorySettingsScreen(
    onBack: () -> Unit,
    onOpenLocations: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    var showLocationPicker by remember { mutableStateOf(false) }

    SettingsSectionScaffold(
        titleRes = R.string.settings_section_inventory,
        onBack = onBack,
        viewModel = viewModel,
    ) {
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
                    valueRange = SOON_MIN..SOON_MAX,
                    steps = SOON_STEPS,
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
}

private const val SOON_MIN = 1f
private const val SOON_MAX = 14f
private const val SOON_STEPS = 12
