package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eatbefore.R
import com.eatbefore.core.designsystem.format.formatDate
import com.eatbefore.core.designsystem.theme.Dimens
import java.time.LocalDate

/** Milliseconds in a day — the unit Material's date picker speaks in. */
private const val MILLIS_PER_DAY = 86_400_000L

/** Presets offered as one-tap chips, in the order they appear. */
private val PRESETS = listOf(
    0L to R.string.add_expiry_today,
    1L to R.string.add_expiry_tomorrow,
    3L to R.string.add_expiry_3_days,
    7L to R.string.add_expiry_week,
    30L to R.string.add_expiry_month,
)

/**
 * Expiration presets plus a way to reach the calendar.
 *
 * A shared component because the same choice is made in three places (adding a product,
 * editing one, buying another package) and they used to disagree: only the edit dialog had
 * a calendar at all, so an unusual date meant saving something wrong and correcting it.
 *
 * The chosen preset stays highlighted — [selected] is compared against the date each chip
 * would produce, so reopening the screen shows what is actually set.
 */
@Composable
fun ExpiryPresetChips(
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate?) -> Unit,
    onPickDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        PRESETS.forEach { (days, labelRes) ->
            val date = today.plusDays(days)
            FilterChip(
                selected = selected == date,
                onClick = { onSelect(date) },
                label = { Text(stringResource(labelRes)) },
            )
        }
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.add_expiry_none)) },
        )
        AssistChip(
            onClick = onPickDate,
            label = { Text(stringResource(R.string.product_edit_pick_date)) },
            leadingIcon = { Icon(Icons.Outlined.EditCalendar, contentDescription = null) },
        )
    }
}

/** The system calendar, pre-set to [initial]. Confirming returns the picked day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryDatePickerDialog(
    initial: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toEpochDay()?.times(MILLIS_PER_DAY),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis
                        ?.let { onConfirm(LocalDate.ofEpochDay(it / MILLIS_PER_DAY)) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

/**
 * Asks for nothing but an expiration date. Used where the rest of the product is already
 * known — buying another package of something already at home.
 */
@Composable
fun ExpiryOnlyDialog(
    title: String,
    today: LocalDate,
    onConfirm: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
    initial: LocalDate? = null,
) {
    var expiry by remember { mutableStateOf(initial) }
    var showCalendar by remember { mutableStateOf(false) }

    if (showCalendar) {
        ExpiryDatePickerDialog(
            initial = expiry,
            onConfirm = { expiry = it },
            onDismiss = { showCalendar = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                ExpiryPresetChips(
                    selected = expiry,
                    today = today,
                    onSelect = { expiry = it },
                    onPickDate = { showCalendar = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = expiry?.let(::formatDate) ?: stringResource(R.string.status_no_date),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(expiry) }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
