package com.eatbefore.feature.common

import com.eatbefore.domain.model.ExpiryStatus
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.StorageType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The axis every stock list is now grouped by. Its boundaries are the whole of it, so
 * they are pinned here: an off-by-one puts today's milk under "tomorrow", which is the
 * one mistake this app cannot afford to make.
 */
class TimeBucketTest {

    @Test
    fun `each boundary falls on the expected side`() {
        assertEquals(TimeBucket.EXPIRED, row(-10).timeBucket())
        assertEquals(TimeBucket.EXPIRED, row(-1).timeBucket())
        assertEquals(TimeBucket.TODAY, row(0).timeBucket())
        assertEquals(TimeBucket.TOMORROW, row(1).timeBucket())
        assertEquals(TimeBucket.THIS_WEEK, row(2).timeBucket())
        // Seven days out is still "this week"; eight is not.
        assertEquals(TimeBucket.THIS_WEEK, row(7).timeBucket())
        assertEquals(TimeBucket.LATER, row(8).timeBucket())
        assertEquals(TimeBucket.LATER, row(365).timeBucket())
    }

    @Test
    fun `a batch with no date is not on the axis at all`() {
        assertEquals(TimeBucket.NO_DATE, row(null).timeBucket())
    }

    @Test
    fun `groups come out nearest first and empty buckets are dropped`() {
        val groups = listOf(row(30), row(-1), row(0), row(30)).groupByTime()

        assertEquals(
            listOf(TimeBucket.EXPIRED, TimeBucket.TODAY, TimeBucket.LATER),
            groups.map { it.bucket },
        )
        assertEquals(listOf(1, 1, 2), groups.map { it.rows.size })
    }

    /** Order inside a bucket is the caller's, so an already-sorted list stays sorted. */
    @Test
    fun `rows keep the order they arrived in`() {
        val first = row(3, id = 1)
        val second = row(5, id = 2)

        val group = listOf(first, second).groupByTime().single()

        assertEquals(listOf(1L, 2L), group.rows.map { it.batchId })
    }

    private fun row(remainingDays: Long?, id: Long = 1) = InventoryRowUi(
        batchId = id,
        productName = "Молоко",
        brand = null,
        quantity = 1.0,
        unit = MeasurementUnit.PIECE,
        locationId = 1,
        locationName = "Fridge",
        locationType = StorageType.FRIDGE,
        // Deliberately unrelated to the bucket: the axis reads the days, not the status.
        expiryStatus = ExpiryStatus.FRESH,
        remainingDays = remainingDays,
        isOpened = false,
    )
}
