package com.eatbefore.feature.product

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.QuantityStepper
import com.eatbefore.core.designsystem.component.SectionCard
import com.eatbefore.core.designsystem.component.SettingActionRow
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.format.formatDate
import com.eatbefore.core.designsystem.format.formatMoney
import com.eatbefore.core.designsystem.format.formatQuantity
import com.eatbefore.core.designsystem.format.shortLabel
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.domain.model.DiscardReason
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType
import com.eatbefore.domain.notification.ReminderDays
import com.eatbefore.domain.shelflife.FreezerShelfLife
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The two settings a product card carries about the future: keep at least this much at
 * home, and remind this far ahead. Below the details because they are about the product,
 * not about this one pack.
 */
@Composable
internal fun StockRulesCard(
    product: Product,
    generalReminderDays: Int,
    onMinQuantity: (Double?) -> Unit,
    onReminderDays: (Int?) -> Unit,
) {
    var editMinimum by remember { mutableStateOf(false) }
    var editReminder by remember { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.product_rules_title)) {
        SettingActionRow(
            title = stringResource(R.string.product_min_quantity),
            subtitle = product.minQuantity?.let { minimum ->
                stringResource(R.string.product_min_quantity_value, formatQuantity(minimum, product.measurementUnit))
            } ?: stringResource(R.string.product_min_quantity_none),
            onClick = { editMinimum = true },
        )
        SettingActionRow(
            title = stringResource(R.string.product_reminder_days),
            subtitle = product.reminderDays?.let { days ->
                pluralStringResource(R.plurals.product_reminder_days_value, days, days)
            } ?: pluralStringResource(R.plurals.product_reminder_days_general, generalReminderDays, generalReminderDays),
            onClick = { editReminder = true },
        )
    }

    if (editMinimum) {
        MinQuantityDialog(
            initial = product.minQuantity,
            unitLabel = product.measurementUnit.shortLabel(),
            onConfirm = { value ->
                editMinimum = false
                onMinQuantity(value)
            },
            onDismiss = { editMinimum = false },
        )
    }
    if (editReminder) {
        ReminderDaysDialog(
            initial = product.reminderDays,
            generalDays = generalReminderDays,
            onConfirm = { days ->
                editReminder = false
                onReminderDays(days)
            },
            onDismiss = { editReminder = false },
        )
    }
}

@Composable
private fun MinQuantityDialog(
    initial: Double?,
    unitLabel: String,
    onConfirm: (Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.let(::plainNumber).orEmpty()) }
    val value = text.toDoubleOrNull()?.takeIf { it > 0.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_min_quantity)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                Text(
                    stringResource(R.string.product_min_quantity_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.replace(',', '.').filter { c -> c.isDigit() || c == '.' } },
                    suffix = { Text(unitLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.product_rule_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun ReminderDaysDialog(
    initial: Int?,
    generalDays: Int,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_reminder_days)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                Text(
                    stringResource(R.string.product_reminder_days_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    FilterChip(
                        selected = chosen == null,
                        onClick = { chosen = null },
                        label = {
                            Text(pluralStringResource(R.plurals.product_reminder_days_general, generalDays, generalDays))
                        },
                    )
                    ReminderDays.OPTIONS.forEach { days ->
                        FilterChip(
                            selected = chosen == days,
                            onClick = { chosen = days },
                            label = { Text(pluralStringResource(R.plurals.product_reminder_days_chip, days, days)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * «Выбросить?» with an optional «почему». The reason is one tap and never required: a
 * dialog that refused to throw things out until a question was answered would get the
 * question answered at random, which is worse than not asking.
 */
@Composable
internal fun DiscardDialog(onConfirm: (DiscardReason?) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf<DiscardReason?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_discard_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                Text(stringResource(R.string.product_discard_confirm_message))
                Text(
                    stringResource(R.string.discard_reason_prompt),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    DiscardReason.entries.forEach { option ->
                        FilterChip(
                            selected = reason == option,
                            onClick = { reason = if (reason == option) null else option },
                            label = { Text(option.label()) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }) { Text(stringResource(R.string.product_action_discard)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
internal fun DiscardReason.label(): String = stringResource(
    when (this) {
        DiscardReason.SPOILED -> R.string.discard_reason_spoiled
        DiscardReason.FORGOT -> R.string.discard_reason_forgot
        DiscardReason.TOO_MUCH -> R.string.discard_reason_too_much
        DiscardReason.DISLIKED -> R.string.discard_reason_disliked
    },
)

/** Whether moving [item] to [target] needs any question at all. */
internal fun moveNeedsDialog(item: InventoryItem, target: StorageLocation): Boolean =
    item.batch.quantity > 1.0 || freezerTransition(item.location.type, target.type) != FreezerTransition.NONE

internal enum class FreezerTransition { NONE, FREEZING, THAWING }

internal fun freezerTransition(from: StorageType, to: StorageType): FreezerTransition = when {
    to == StorageType.FREEZER && from != StorageType.FREEZER -> FreezerTransition.FREEZING
    from == StorageType.FREEZER && to != StorageType.FREEZER -> FreezerTransition.THAWING
    else -> FreezerTransition.NONE
}

/**
 * Moving a pack, with the two questions that matter for the freezer: how much (half the
 * mince in, half for tonight), and which date it keeps from now on. The suggested date is
 * chosen by default because it is almost always right; keeping the old one is one tap.
 */
@Composable
internal fun MoveDialog(
    item: InventoryItem,
    target: StorageLocation,
    today: LocalDate,
    onConfirm: (quantity: Double?, newExpiry: LocalDate?, changeExpiry: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val transition = freezerTransition(item.location.type, target.type)
    val suggested = when (transition) {
        FreezerTransition.FREEZING ->
            today.plusDays(FreezerShelfLife.frozenDays(item.product.name, item.product.category).toLong())
        FreezerTransition.THAWING ->
            today.plusDays(FreezerShelfLife.thawedDays(item.product.name, item.product.category).toLong())
        FreezerTransition.NONE -> null
    }
    val total = item.batch.quantity
    var amount by remember { mutableStateOf(total) }
    var useSuggested by remember { mutableStateOf(suggested != null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_move_title, target.displayName())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                if (total > 1.0) {
                    Text(stringResource(R.string.product_move_amount), style = MaterialTheme.typography.labelLarge)
                    QuantityStepper(
                        onDecrease = { amount = (amount - 1.0).coerceAtLeast(1.0) },
                        onIncrease = { amount = (amount + 1.0).coerceAtMost(total) },
                        decreaseEnabled = amount > 1.0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.product_move_amount_of,
                                formatQuantity(amount, item.batch.measurementUnit),
                                formatQuantity(total, item.batch.measurementUnit),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (suggested != null) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                    ) {
                        FilterChip(
                            selected = useSuggested,
                            onClick = { useSuggested = true },
                            label = {
                                Text(
                                    stringResource(
                                        if (transition == FreezerTransition.FREEZING) {
                                            R.string.product_move_frozen_date
                                        } else {
                                            R.string.product_move_thawed_date
                                        },
                                        formatDate(suggested),
                                    ),
                                )
                            },
                        )
                        FilterChip(
                            selected = !useSuggested,
                            onClick = { useSuggested = false },
                            label = {
                                Text(
                                    item.batch.expirationDate?.let {
                                        stringResource(R.string.product_move_keep_date, formatDate(it))
                                    } ?: stringResource(R.string.product_move_keep_no_date),
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(amount.takeIf { it < total }, suggested.takeIf { useSuggested }, useSuggested && suggested != null)
            }) { Text(stringResource(R.string.product_move_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * What the product has cost, purchase by purchase, per unit. Only shown once a price was
 * entered at least once; most products never get one, and an empty «Цены» card would be
 * a nag rather than information.
 */
@Composable
internal fun PriceHistoryCard(history: PriceHistory) {
    val latest = history.latest
    val perUnit = latest.unit.shortLabel()
    SectionCard(title = stringResource(R.string.product_price_history)) {
        Text(
            stringResource(R.string.product_price_latest, formatMoney(latest.unitPrice, latest.currency), perUnit),
            style = MaterialTheme.typography.titleMedium,
        )
        history.change?.let { change ->
            val percent = (change * PERCENT).roundToInt()
            Text(
                when {
                    percent > 0 -> stringResource(R.string.product_price_up, percent)
                    percent < 0 -> stringResource(R.string.product_price_down, -percent)
                    else -> stringResource(R.string.product_price_same)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (percent > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (history.points.size > 1) {
            Text(
                stringResource(R.string.product_price_average, formatMoney(history.average, latest.currency), perUnit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            history.points.forEach { point ->
                Text(
                    "${formatDate(point.date)} — ${formatMoney(point.unitPrice, point.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun plainNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private const val PERCENT = 100
