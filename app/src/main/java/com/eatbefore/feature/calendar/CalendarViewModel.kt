package com.eatbefore.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

data class CalendarUiState(
    val isLoading: Boolean = true,
    val month: CalendarMonth? = null,
    val selected: LocalDate? = null,
    /** What runs out on [selected]. */
    val selectedItems: List<InventoryItem> = emptyList(),
)

private data class CalendarSelection(val month: YearMonth, val day: LocalDate)

@HiltViewModel
class CalendarViewModel @Inject constructor(inventoryRepository: InventoryRepository, private val clock: AppClock) : ViewModel() {

    private val selection = MutableStateFlow(CalendarSelection(YearMonth.from(clock.today()), clock.today()))

    val uiState: StateFlow<CalendarUiState> = combine(
        inventoryRepository.observePresentByExpiry(),
        selection,
    ) { items, picked ->
        CalendarUiState(
            isLoading = false,
            month = buildCalendarMonth(
                month = picked.month,
                items = items,
                today = clock.today(),
                firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
            ),
            selected = picked.day,
            selectedItems = itemsDueOn(picked.day, items),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun select(day: LocalDate) = selection.update { it.copy(day = day) }

    /** Paging keeps the selected day when it is in view, and otherwise moves to the 1st. */
    fun showMonth(offset: Long) = selection.update { current ->
        val month = current.month.plusMonths(offset)
        val today = clock.today()
        current.copy(month = month, day = if (YearMonth.from(today) == month) today else month.atDay(1))
    }
}
