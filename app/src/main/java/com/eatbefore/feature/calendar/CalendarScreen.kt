package com.eatbefore.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.AppCard
import com.eatbefore.core.designsystem.component.LoadingState
import com.eatbefore.core.designsystem.component.ScreenScaffold
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.format.formatDate
import com.eatbefore.core.designsystem.format.formatQuantity
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.domain.model.InventoryItem
import java.time.format.TextStyle
import java.util.Locale

/**
 * The month ahead, with what runs out on each day: for planning the week's meals around
 * what has to be eaten, rather than reading the list top to bottom every evening.
 */
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenBatch: (Long) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // From composition rather than Locale.getDefault(): month and weekday names then follow
    // a language change without restarting the screen.
    val locale = LocalLocale.current.platformLocale
    ScreenScaffold(title = stringResource(R.string.calendar_title), onBack = onBack) { padding ->
        val month = state.month
        if (state.isLoading || month == null) {
            LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            item(key = "header") {
                MonthHeader(
                    title = month.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                        .replaceFirstChar { it.uppercase() } + " " + month.month.year,
                    onPrevious = { viewModel.showMonth(-1) },
                    onNext = { viewModel.showMonth(1) },
                )
            }
            item(key = "grid") {
                MonthGrid(month = month, selected = state.selected, locale = locale, onSelect = viewModel::select)
            }
            item(key = "day") {
                Text(
                    state.selected?.let { formatDate(it) }.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Dimens.spaceMd),
                )
            }
            if (state.selectedItems.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.calendar_day_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.selectedItems, key = { it.batch.id }) { item ->
                DueRow(item = item, onClick = { onOpenBatch(item.batch.id) })
            }
        }
    }
}

@Composable
private fun MonthHeader(title: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_previous))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next))
        }
    }
}

@Composable
private fun MonthGrid(
    month: CalendarMonth,
    selected: java.time.LocalDate?,
    locale: Locale,
    onSelect: (java.time.LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            month.weeks.first().forEach { day ->
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        month.weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                week.forEach { day ->
                    DayCell(day = day, selected = day.date == selected, onClick = { onSelect(day.date) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        selected -> colors.primaryContainer
        day.isToday -> colors.surfaceContainerHighest
        else -> colors.surfaceContainerLow
    }
    val countLabel = if (day.batchCount > 0) stringResource(R.string.calendar_day_count, day.batchCount) else ""
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${formatDateForA11y(day)} $countLabel" }
            .padding(Dimens.spaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (day.inMonth) colors.onSurface else colors.onSurfaceVariant.copy(alpha = OUT_OF_MONTH_ALPHA),
        )
        if (day.batchCount > 0) {
            // Past days that still hold something are the ones already gone: error colour.
            Box(
                modifier = Modifier
                    .size(Dimens.spaceLg)
                    .clip(CircleShape)
                    .background(if (day.isPast) colors.error else colors.tertiary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.batchCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.isPast) colors.onError else colors.onTertiary,
                )
            }
        }
    }
}

private fun formatDateForA11y(day: CalendarDay): String = formatDate(day.date)

@Composable
private fun DueRow(item: InventoryItem, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().padding(Dimens.spaceMd)) {
            Text(item.product.name, style = MaterialTheme.typography.titleMedium)
            Text(
                item.location.displayName() + " · " + formatQuantity(item.batch.quantity, item.batch.measurementUnit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val OUT_OF_MONTH_ALPHA = 0.5f
