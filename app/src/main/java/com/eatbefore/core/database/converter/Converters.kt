package com.eatbefore.core.database.converter

import androidx.room.TypeConverter
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.ProductSource
import com.eatbefore.domain.model.ShoppingPriority
import com.eatbefore.domain.model.StorageType
import java.time.Instant
import java.time.LocalDate

/**
 * Room type converters. Timestamps are stored as epoch millis ([Instant]) and dates as
 * epoch days ([LocalDate]) — compact, index-friendly, and timezone-stable. Enums are
 * stored by name; unknown names decode to a safe default so a corrupted/older row never
 * crashes the app.
 */
class Converters {

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun batchStatusToString(value: BatchStatus): String = value.name

    @TypeConverter
    fun stringToBatchStatus(value: String): BatchStatus =
        enumValueOrDefault(value, BatchStatus.ACTIVE)

    @TypeConverter
    fun eventTypeToString(value: EventType): String = value.name

    @TypeConverter
    fun stringToEventType(value: String): EventType =
        enumValueOrDefault(value, EventType.UPDATED)

    @TypeConverter
    fun barcodeTypeToString(value: BarcodeType): String = value.name

    @TypeConverter
    fun stringToBarcodeType(value: String): BarcodeType =
        enumValueOrDefault(value, BarcodeType.NONE)

    @TypeConverter
    fun storageTypeToString(value: StorageType): String = value.name

    @TypeConverter
    fun stringToStorageType(value: String): StorageType =
        enumValueOrDefault(value, StorageType.OTHER)

    @TypeConverter
    fun measurementUnitToString(value: MeasurementUnit): String = value.name

    @TypeConverter
    fun stringToMeasurementUnit(value: String): MeasurementUnit =
        enumValueOrDefault(value, MeasurementUnit.PIECE)

    @TypeConverter
    fun productSourceToString(value: ProductSource): String = value.name

    @TypeConverter
    fun stringToProductSource(value: String): ProductSource =
        enumValueOrDefault(value, ProductSource.USER)

    @TypeConverter
    fun shoppingPriorityToString(value: ShoppingPriority): String = value.name

    @TypeConverter
    fun stringToShoppingPriority(value: String): ShoppingPriority =
        enumValueOrDefault(value, ShoppingPriority.NORMAL)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
