package com.eatbefore.core.backup

import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Every batch ever recorded — at home now and long gone — as one table to open in a
 * spreadsheet.
 *
 * The JSON backup is for the app; this is for people: sorting by price, totting up a
 * month, printing the pantry. Laid out the way a Russian Excel reads a file without being
 * told how: `;` between columns, `,` in decimals, a byte-order mark so Cyrillic is not
 * taken for Latin-1, and ISO dates, which every spreadsheet recognises.
 */
object StockCsv {

    data class Labels(
        val headers: List<String>,
        val status: (BatchStatus) -> String,
        val unit: (com.eatbefore.domain.model.MeasurementUnit) -> String,
        /** Preset places are stored under English keys and shown under their own names. */
        val location: (StorageLocation) -> String = { it.name },
    )

    fun build(
        batches: List<InventoryBatch>,
        products: Map<Long, Product>,
        locations: Map<Long, StorageLocation>,
        labels: Labels,
        zone: ZoneId,
        locale: Locale = Locale.getDefault(),
    ): String {
        val number = DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale))
        val rows = batches
            .sortedWith(compareByDescending<InventoryBatch> { it.status.isPresent && it.deletedAt == null }.thenByDescending { it.addedAt })
            .map { batch ->
                val product = products[batch.productId]
                listOf(
                    product?.name.orEmpty(),
                    product?.brand.orEmpty(),
                    product?.category.orEmpty(),
                    locations[batch.storageLocationId]?.let(labels.location).orEmpty(),
                    number.format(batch.quantity),
                    labels.unit(batch.measurementUnit),
                    labels.status(batch.status),
                    batch.purchaseDate?.toString().orEmpty(),
                    batch.expirationDate?.toString().orEmpty(),
                    batch.openedAt?.let { date(it, zone) }.orEmpty(),
                    batch.price?.let(number::format).orEmpty(),
                    batch.currency.orEmpty(),
                    batch.note.orEmpty(),
                )
            }
        return BOM + (listOf(labels.headers) + rows).joinToString(LINE_END) { row -> row.joinToString(SEPARATOR) { cell(it) } } + LINE_END
    }

    /** Quoted when it has to be, with quotes doubled — the one escaping rule CSV has. */
    private fun cell(value: String): String =
        if (value.any { it in NEEDS_QUOTES }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun date(instant: Instant, zone: ZoneId): String = instant.atZone(zone).toLocalDate().toString()

    private const val NEEDS_QUOTES = ";\"\n\r"
    private const val BOM = "\uFEFF"
    private const val SEPARATOR = ";"
    private const val LINE_END = "\r\n"
}
