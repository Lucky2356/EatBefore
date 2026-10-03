package com.eatbefore.core.backup

import android.content.Context
import com.eatbefore.R
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.database.EatBeforeDatabase
import com.eatbefore.data.mapper.toDomain
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Reads everything [StockCsv] needs and labels it in the app's language. */
class StockCsvExporter @Inject constructor(
    private val db: EatBeforeDatabase,
    @ApplicationContext private val context: Context,
    private val clock: AppClock,
) {

    suspend fun export(): String = StockCsv.build(
        batches = db.inventoryBatchDao().getAll().map { it.toDomain() },
        products = db.productDao().getAll().associate { it.id to it.toDomain() },
        locations = db.storageLocationDao().getAll().associate { it.id to it.toDomain() },
        labels = StockCsv.Labels(
            headers = HEADERS.map(context::getString),
            status = { context.getString(statusLabel(it)) },
            unit = { context.getString(unitLabel(it)) },
            location = ::locationLabel,
        ),
        zone = clock.zone(),
    )

    private fun locationLabel(location: StorageLocation): String =
        if (location.name in PRESET_NAMES) context.getString(storageLabel(location.type)) else location.name

    private companion object {
        val HEADERS = listOf(
            R.string.csv_product, R.string.csv_brand, R.string.csv_category, R.string.csv_location,
            R.string.csv_quantity, R.string.csv_unit, R.string.csv_status, R.string.csv_bought,
            R.string.csv_expires, R.string.csv_opened, R.string.csv_price, R.string.csv_currency, R.string.csv_note,
        )
        val PRESET_NAMES = setOf("Fridge", "Freezer", "Cupboard", "Pantry")

        fun statusLabel(status: BatchStatus): Int = when (status) {
            BatchStatus.ACTIVE -> R.string.csv_status_active
            BatchStatus.OPENED -> R.string.csv_status_opened
            BatchStatus.PARTIALLY_USED -> R.string.csv_status_partial
            BatchStatus.CONSUMED -> R.string.csv_status_consumed
            BatchStatus.DISCARDED -> R.string.csv_status_discarded
            BatchStatus.EXPIRED -> R.string.csv_status_expired
            BatchStatus.ARCHIVED -> R.string.csv_status_archived
        }

        fun unitLabel(unit: MeasurementUnit): Int = when (unit) {
            MeasurementUnit.PIECE -> R.string.unit_piece
            MeasurementUnit.GRAM -> R.string.unit_gram
            MeasurementUnit.KILOGRAM -> R.string.unit_kilogram
            MeasurementUnit.MILLILITER -> R.string.unit_milliliter
            MeasurementUnit.LITER -> R.string.unit_liter
            MeasurementUnit.PACKAGE -> R.string.unit_package
            MeasurementUnit.PERCENT -> R.string.unit_percent
        }

        fun storageLabel(type: StorageType): Int = when (type) {
            StorageType.FRIDGE -> R.string.storage_fridge
            StorageType.FREEZER -> R.string.storage_freezer
            StorageType.CUPBOARD -> R.string.storage_cupboard
            StorageType.PANTRY -> R.string.storage_pantry
            StorageType.OTHER -> R.string.storage_other
        }
    }
}
