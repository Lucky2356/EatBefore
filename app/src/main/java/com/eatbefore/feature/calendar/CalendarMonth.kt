package com.eatbefore.feature.calendar

import com.eatbefore.domain.model.InventoryItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** One square of the month grid. */
data class CalendarDay(
    val date: LocalDate,
    /** False for the days of the neighbouring months that fill the first and last week. */
    val inMonth: Boolean,
    /** Batches whose date — after opening, if that comes first — falls on this day. */
    val batchCount: Int,
    val isToday: Boolean,
    /** Before today and still at home: these are the ones already gone. */
    val isPast: Boolean,
)

/** A month laid out in whole weeks, starting on [firstDayOfWeek]. */
data class CalendarMonth(val month: YearMonth, val weeks: List<List<CalendarDay>>)

/**
 * Lays the stock out over a month: which days something runs out on.
 *
 * The lists are ordered by date already, but a list does not show that Thursday has four
 * things and the weekend none — which is what planning the week's meals needs. The date
 * used is the effective one, so an opened carton sits on the day it actually has to go.
 */
fun buildCalendarMonth(
    month: YearMonth,
    items: List<InventoryItem>,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): CalendarMonth {
    val counts = items.mapNotNull { it.batch.effectiveExpirationDate }.groupingBy { it }.eachCount()
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(firstDayOfWeek.minus(1)))
    val days = generateSequence(first) { it.plusDays(1) }
        .takeWhile { !it.isAfter(last) }
        .map { date ->
            CalendarDay(
                date = date,
                inMonth = YearMonth.from(date) == month,
                batchCount = counts[date] ?: 0,
                isToday = date == today,
                isPast = date.isBefore(today),
            )
        }
        .toList()
    return CalendarMonth(month = month, weeks = days.chunked(DAYS_IN_WEEK))
}

/** Batches due on [date], by name. */
fun itemsDueOn(date: LocalDate, items: List<InventoryItem>): List<InventoryItem> =
    items.filter { it.batch.effectiveExpirationDate == date }.sortedBy { it.product.name.lowercase() }

private const val DAYS_IN_WEEK = 7
