package com.eatbefore.core.designsystem.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eatbefore.R
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType

/** Short unit label. The app is Russian-first; labels are concise on purpose. */
fun MeasurementUnit.shortLabel(): String = when (this) {
    MeasurementUnit.PIECE -> "шт"
    MeasurementUnit.GRAM -> "г"
    MeasurementUnit.KILOGRAM -> "кг"
    MeasurementUnit.MILLILITER -> "мл"
    MeasurementUnit.LITER -> "л"
    MeasurementUnit.PACKAGE -> "уп"
    MeasurementUnit.PERCENT -> "%"
}

/** Formats a quantity without a trailing ".0" for whole numbers. */
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

/**
 * Display name for a location: preset locations (seeded with English keys) show their
 * localized [StorageType] label; custom locations show the user-entered name.
 */
@Composable
fun StorageLocation.displayName(): String =
    if (type == StorageType.OTHER) name else type.label()

/** Human remaining-time text derived from days until the effective expiration. */
@Composable
fun remainingText(remainingDays: Long?): String? = when {
    remainingDays == null -> null
    remainingDays < 0 -> stringResource(R.string.expired_days_ago, (-remainingDays).toInt())
    remainingDays == 0L -> stringResource(R.string.expires_today)
    remainingDays == 1L -> stringResource(R.string.expires_tomorrow)
    else -> stringResource(R.string.expires_in_days, remainingDays.toInt())
}
