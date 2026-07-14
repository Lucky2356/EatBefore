package com.eatbefore.feature.addmanual

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.format.shortLabel
import com.eatbefore.domain.model.MeasurementUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onCaptureExpiry: () -> Unit = {},
    recognizedExpiryEpochDay: Long? = null,
    viewModel: AddManualViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedBatchId) {
        if (state.savedBatchId != null) onSaved()
    }

    // Apply a date recognized by the OCR screen when returning to this form.
    LaunchedEffect(recognizedExpiryEpochDay) {
        if (recognizedExpiryEpochDay != null) {
            viewModel.onExpirationDate(java.time.LocalDate.ofEpochDay(recognizedExpiryEpochDay))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.add_name)) },
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text(stringResource(R.string.add_error_name_required)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.brand,
                onValueChange = viewModel::onBrand,
                label = { Text(stringResource(R.string.add_brand)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.quantity,
                    onValueChange = viewModel::onQuantity,
                    label = { Text(stringResource(R.string.add_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            LabeledSection(stringResource(R.string.add_unit)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MeasurementUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = state.unit == unit,
                            onClick = { viewModel.onUnit(unit) },
                            label = { Text(unit.shortLabel()) },
                        )
                    }
                }
            }

            LabeledSection(stringResource(R.string.add_location)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.locations.forEach { location ->
                        FilterChip(
                            selected = state.selectedLocationId == location.id,
                            onClick = { viewModel.onLocation(location.id) },
                            label = { Text(location.displayName()) },
                        )
                    }
                }
            }

            LabeledSection(stringResource(R.string.add_expiration)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExpiryChip(R.string.add_expiry_today, 0L, viewModel)
                    ExpiryChip(R.string.add_expiry_tomorrow, 1L, viewModel)
                    ExpiryChip(R.string.add_expiry_3_days, 3L, viewModel)
                    ExpiryChip(R.string.add_expiry_week, 7L, viewModel)
                    ExpiryChip(R.string.add_expiry_month, 30L, viewModel)
                    FilterChip(
                        selected = state.expirationDate == null,
                        onClick = { viewModel.onQuickExpiry(null) },
                        label = { Text(stringResource(R.string.add_expiry_none)) },
                    )
                }
            }
            state.expirationDate?.let { date ->
                Text(
                    text = "${stringResource(R.string.add_expiration)}: $date",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = onCaptureExpiry, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.ocr_from_photo))
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                label = { Text(stringResource(R.string.add_note)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun ExpiryChip(
    labelRes: Int,
    daysFromToday: Long,
    viewModel: AddManualViewModel,
) {
    // The exact chosen date is shown separately below, so chips act as one-tap presets.
    FilterChip(
        selected = false,
        onClick = { viewModel.onQuickExpiry(daysFromToday) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun LabeledSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
