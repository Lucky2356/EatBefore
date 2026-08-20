package com.eatbefore.feature.addmanual

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.AppCard
import com.eatbefore.core.designsystem.component.ExpiryDatePickerDialog
import com.eatbefore.core.designsystem.component.ExpiryPresetChips
import com.eatbefore.core.designsystem.component.QuantityStepper
import com.eatbefore.core.designsystem.component.ScreenScaffold
import com.eatbefore.core.designsystem.format.currencySymbol
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.format.formatDate
import com.eatbefore.core.designsystem.format.remainingText
import com.eatbefore.core.designsystem.format.shortLabel
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.domain.model.MeasurementUnit
import java.time.temporal.ChronoUnit

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
    val snackbarHost = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        ExpiryDatePickerDialog(
            initial = state.expirationDate,
            onConfirm = viewModel::onExpirationDate,
            onDismiss = { showDatePicker = false },
        )
    }

    // Saving navigates away, unless we are still asking whether to share the product
    // with the open catalog — that question would be lost on the next screen.
    LaunchedEffect(state.savedBatchId, state.contributeOffer, state.message) {
        if (state.savedBatchId != null && state.contributeOffer == null && state.message == null) {
            onSaved()
        }
    }

    val messageText = state.message?.let { stringResource(it) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHost.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    // Apply a date recognized by the OCR screen when returning to this form.
    LaunchedEffect(recognizedExpiryEpochDay) {
        if (recognizedExpiryEpochDay != null) {
            viewModel.onExpirationDate(java.time.LocalDate.ofEpochDay(recognizedExpiryEpochDay))
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.add_title),
        onBack = onBack,
        snackbarHostState = snackbarHost,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.add_name)) },
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text(stringResource(R.string.add_error_name_required)) }
                } else {
                    null
                },
                singleLine = true,
                // Product names read as sentences ("Молоко 3,5 %"), so start capitalized.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.brand,
                onValueChange = viewModel::onBrand,
                label = { Text(stringResource(R.string.add_brand)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.barcode,
                onValueChange = viewModel::onBarcode,
                label = { Text(stringResource(R.string.add_barcode)) },
                supportingText = { Text(stringResource(R.string.add_barcode_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            LabeledSection(stringResource(R.string.add_location)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
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

            // The one question this screen is really asking, given its own block above
            // the amount. It used to be the fifth field, between the barcode and the unit
            // of measure, which is no place for it on a screen about when things run out.
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                ) {
                    Text(
                        text = stringResource(R.string.add_expiration),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ExpiryPresetChips(
                        selected = state.expirationDate,
                        today = viewModel.today,
                        onSelect = viewModel::onExpirationDate,
                        onPickDate = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        suggestedDays = state.suggestedShelfLifeDays,
                    )
                    // A preset says "in three days"; only the date says which day that is,
                    // and the date is what the notification will fire on.
                    state.expirationDate?.let { date ->
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                            Text(
                                text = formatDate(date),
                                style = MaterialTheme.typography.displaySmall,
                            )
                            remainingText(ChronoUnit.DAYS.between(viewModel.today, date))?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = onCaptureExpiry, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.padding(end = Dimens.spaceSm),
                        )
                        Text(stringResource(R.string.ocr_from_photo))
                    }
                }
            }
            QuantityStepper(
                onDecrease = { viewModel.stepQuantity(-1) },
                onIncrease = { viewModel.stepQuantity(+1) },
                decreaseEnabled = (state.quantity.toDoubleOrNull() ?: 0.0) > 1.0,
                modifier = Modifier.fillMaxWidth(),
            ) {
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
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
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

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                label = { Text(stringResource(R.string.add_note)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Optional, and last on the screen on purpose: nothing here blocks saving, and
            // the reward for filling it in arrives later, on the analytics screen.
            OutlinedTextField(
                value = state.price,
                onValueChange = viewModel::onPrice,
                label = { Text(stringResource(R.string.add_price, currencySymbol())) },
                supportingText = { Text(stringResource(R.string.add_price_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = Dimens.spaceSm))
                }
                Text(stringResource(R.string.action_save))
            }
        }
    }

    state.contributeOffer?.let { offer ->
        AlertDialog(
            onDismissRequest = viewModel::declineContribution,
            title = { Text(stringResource(R.string.contribute_title)) },
            text = { Text(stringResource(R.string.contribute_message, offer.name)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmContribution,
                    enabled = !state.isContributing,
                ) { Text(stringResource(R.string.contribute_action)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::declineContribution) {
                    Text(stringResource(R.string.contribute_skip))
                }
            },
        )
    }
}

@Composable
private fun LabeledSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
