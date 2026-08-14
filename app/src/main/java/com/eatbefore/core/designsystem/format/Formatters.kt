package com.eatbefore.core.designsystem.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eatbefore.R
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/**
 * Localized date for a moment in time, rendered in the device zone. Never use
 * Instant.toString() for UI: it prints the UTC date, which is off by one near midnight.
 */
fun formatDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(instant.atZone(zone))

/** Localized date + time (e.g. history rows), rendered in the device zone. */
fun formatDateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .format(instant.atZone(zone))

/** Localized calendar date (expiration dates and similar zone-less values). */
fun formatDate(date: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .format(date)

/**
 * The currency of the phone's locale, stored alongside a price so a number is never left
 * without its unit. Falls back to the rouble: the app is Russian-first, and a price with
 * no currency at all is worse than one that may need correcting.
 */
fun defaultCurrencyCode(): String = runCatching {
    Currency.getInstance(Locale.getDefault()).currencyCode
}.getOrDefault(FALLBACK_CURRENCY)

/** Just the symbol ("₽", "€"), for labelling the price field. */
fun currencySymbol(code: String? = null): String = runCatching {
    Currency.getInstance(code ?: defaultCurrencyCode()).getSymbol(Locale.getDefault())
}.getOrDefault(code.orEmpty())

/** A price with its currency, rounded to whole units — kopecks are noise in a fridge. */
fun formatMoney(amount: Double, code: String?): String = runCatching {
    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        currency = Currency.getInstance(code ?: defaultCurrencyCode())
        maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    }.format(amount)
}.getOrDefault("$amount ${code.orEmpty()}".trim())

private const val FALLBACK_CURRENCY = "RUB"

/** Short, localized unit label. */
@Composable
fun MeasurementUnit.shortLabel(): String = stringResource(
    when (this) {
        MeasurementUnit.PIECE -> R.string.unit_piece
        MeasurementUnit.GRAM -> R.string.unit_gram
        MeasurementUnit.KILOGRAM -> R.string.unit_kilogram
        MeasurementUnit.MILLILITER -> R.string.unit_milliliter
        MeasurementUnit.LITER -> R.string.unit_liter
        MeasurementUnit.PACKAGE -> R.string.unit_package
        MeasurementUnit.PERCENT -> R.string.unit_percent
    },
)

/** Formats a quantity without a trailing ".0" for whole numbers. */
@Composable
fun formatQuantity(value: Double, unit: MeasurementUnit): String {
    val number = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    return "$number ${unit.shortLabel()}"
}

@Composable
fun StorageType.label(): String = stringResource(
    when (this) {
        StorageType.FRIDGE -> R.string.storage_fridge
        StorageType.FREEZER -> R.string.storage_freezer
        StorageType.CUPBOARD -> R.string.storage_cupboard
        StorageType.PANTRY -> R.string.storage_pantry
        StorageType.OTHER -> R.string.storage_other
    },
)

/** Seed names of the preset locations (DefaultStorageLocations, locale-neutral keys). */
private val PRESET_LOCATION_NAMES = setOf("Fridge", "Freezer", "Cupboard", "Pantry")

/**
 * Display name for a location: preset locations (seeded with English keys) show their
 * localized [StorageType] label; custom locations show the user-entered name.
 */
@Composable
fun storageDisplayName(name: String, type: StorageType): String =
    if (name in PRESET_LOCATION_NAMES) type.label() else name

@Composable
fun StorageLocation.displayName(): String = storageDisplayName(name, type)

/** Human remaining-time text derived from days until the effective expiration. */
@Composable
fun remainingText(remainingDays: Long?): String? = when {
    remainingDays == null -> null
    remainingDays < 0 -> stringResource(R.string.expired_days_ago, (-remainingDays).toInt())
    remainingDays == 0L -> stringResource(R.string.expires_today)
    remainingDays == 1L -> stringResource(R.string.expires_tomorrow)
    else -> stringResource(R.string.expires_in_days, remainingDays.toInt())
}
