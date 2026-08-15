package com.eatbefore.feature.analytics

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.AppCard
import com.eatbefore.core.designsystem.component.EmptyState
import com.eatbefore.core.designsystem.component.ScreenScaffold
import com.eatbefore.core.designsystem.component.StatTile
import com.eatbefore.core.designsystem.component.StatTone
import com.eatbefore.core.designsystem.format.displayName
import com.eatbefore.core.designsystem.format.formatMoney
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.LocalStatusColors
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.usecase.AnalyticsSummary
import com.eatbefore.domain.usecase.BuildAnalyticsUseCase
import com.eatbefore.domain.usecase.WeeklyStat
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reportText = state.summary?.let { buildReportText(it, state.period, state.byLocation) }

    ScreenScaffold(
        title = stringResource(R.string.analytics_title),
        onBack = onBack,
        actions = {
            if (reportText != null && state.summary?.hasData == true) {
                IconButton(onClick = {
                    // Plain-text report via the system share sheet; the user picks the target.
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, reportText)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.analytics_share),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                PeriodChip(R.string.analytics_period_week, AnalyticsPeriod.WEEK, state.period, viewModel::setPeriod)
                PeriodChip(R.string.analytics_period_month, AnalyticsPeriod.MONTH, state.period, viewModel::setPeriod)
                PeriodChip(R.string.analytics_period_year, AnalyticsPeriod.YEAR, state.period, viewModel::setPeriod)
                PeriodChip(R.string.analytics_period_all, AnalyticsPeriod.ALL, state.period, viewModel::setPeriod)
            }

            val summary = state.summary
            if (!state.isLoading && summary != null && !summary.hasData) {
                EmptyState(message = stringResource(R.string.analytics_empty))
            } else if (summary != null) {
                SummaryContent(summary)
                if (state.byLocation.isNotEmpty()) {
                    SectionList(
                        title = stringResource(R.string.analytics_by_location),
                        rows = state.byLocation.map { (location, count) ->
                            location.displayName() to count
                        },
                    )
                }
            }
        }
    }
}

/** Human-readable plain-text report for the system share sheet. */
@Composable
private fun buildReportText(
    summary: AnalyticsSummary,
    period: AnalyticsPeriod,
    byLocation: List<Pair<StorageLocation, Int>>,
): String {
    val periodLabel = stringResource(
        when (period) {
            AnalyticsPeriod.WEEK -> R.string.analytics_period_week
            AnalyticsPeriod.MONTH -> R.string.analytics_period_month
            AnalyticsPeriod.YEAR -> R.string.analytics_period_year
            AnalyticsPeriod.ALL -> R.string.analytics_period_all
        },
    )
    return buildString {
        appendLine(stringResource(R.string.analytics_report_title))
        appendLine(stringResource(R.string.analytics_report_period, periodLabel))
        appendLine()
        appendLine("${stringResource(R.string.analytics_added)}: ${summary.addedCount}")
        appendLine("${stringResource(R.string.analytics_consumed)}: ${summary.consumedCount}")
        appendLine("${stringResource(R.string.analytics_discarded)}: ${summary.discardedCount}")
        appendLine("${stringResource(R.string.analytics_expired)}: ${summary.expiredCount}")
        summary.usedInTimePercent?.let {
            appendLine(stringResource(R.string.analytics_used_in_time, it))
        }
        if (summary.wastedByCategory.isNotEmpty()) {
            appendLine()
            appendLine(stringResource(R.string.analytics_wasted_by_category) + ":")
            summary.wastedByCategory.forEach { (category, count) ->
                val label = category.ifBlank { stringResource(R.string.analytics_no_category) }
                appendLine("  $label — $count")
            }
        }
        if (summary.topAddedProducts.isNotEmpty()) {
            appendLine()
            appendLine(stringResource(R.string.analytics_top_products) + ":")
            summary.topAddedProducts.forEach { (name, count) ->
                appendLine("  $name — $count")
            }
        }
        if (byLocation.isNotEmpty()) {
            appendLine()
            appendLine(stringResource(R.string.analytics_by_location) + ":")
            byLocation.forEach { (location, count) ->
                appendLine("  ${location.displayName()} — $count")
            }
        }
    }.trimEnd()
}

@Composable
private fun SummaryContent(summary: AnalyticsSummary) {
    val statusColors = LocalStatusColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        StatTile(
            label = stringResource(R.string.analytics_added),
            value = summary.addedCount,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.analytics_consumed),
            value = summary.consumedCount,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        // These two are the only numbers here that are bad news — and only when they are
        // not zero. Nothing wasted is a good week, and it used to be painted pink.
        StatTile(
            label = stringResource(R.string.analytics_discarded),
            value = summary.discardedCount,
            tone = StatTone.ATTENTION,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.analytics_expired),
            value = summary.expiredCount,
            tone = StatTone.ATTENTION,
            modifier = Modifier.weight(1f),
        )
    }

    // The one number that makes waste feel like waste. Shown with its coverage: prices are
    // optional, so a bare total would read as the whole loss when it may be a third of it.
    summary.wastedMoney?.let { money ->
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Dimens.spaceLg),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                Text(
                    stringResource(
                        R.string.analytics_wasted_money,
                        formatMoney(money.amount, money.currency),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColors.expired.content,
                )
                Text(
                    stringResource(
                        R.string.analytics_wasted_money_coverage,
                        money.coveredBatches,
                        money.wastedBatches,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    summary.usedInTimePercent?.let { percent ->
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Dimens.spaceLg), verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                Text(
                    stringResource(R.string.analytics_used_in_time, percent),
                    style = MaterialTheme.typography.titleMedium,
                )
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = statusColors.fresh.content,
                )
            }
        }
    }

    if (summary.weeklyTrend.size >= 2) {
        WeeklyTrendCard(summary.weeklyTrend)
    }

    if (summary.wastedByCategory.isNotEmpty()) {
        SectionList(
            title = stringResource(R.string.analytics_wasted_by_category),
            rows = summary.wastedByCategory.map { (category, count) ->
                val label = if (category == BuildAnalyticsUseCase.UNCATEGORIZED) {
                    stringResource(R.string.analytics_no_category)
                } else {
                    category
                }
                label to count
            },
        )
    }

    if (summary.topAddedProducts.isNotEmpty()) {
        SectionList(
            title = stringResource(R.string.analytics_top_products),
            rows = summary.topAddedProducts,
        )
    }
}

/** Grouped bar chart: added vs wasted per week. Values are also readable from labels. */
@Composable
private fun WeeklyTrendCard(trend: List<WeeklyStat>) {
    val max = trend.maxOf { maxOf(it.added, it.wasted) }.coerceAtLeast(1)
    val barMaxHeight = 96.dp
    val weekFormatter = DateTimeFormatter.ofPattern("dd.MM")
    val addedColor = MaterialTheme.colorScheme.primary
    val wastedColor = MaterialTheme.colorScheme.error

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.spaceLg), verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
            Text(stringResource(R.string.analytics_trend), style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
                verticalAlignment = Alignment.Bottom,
            ) {
                trend.forEach { week ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            TrendBar(week.added, max, barMaxHeight, addedColor)
                            TrendBar(week.wasted, max, barMaxHeight, wastedColor)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            week.weekStart.format(weekFormatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg)) {
                LegendDot(addedColor, stringResource(R.string.analytics_trend_added))
                LegendDot(wastedColor, stringResource(R.string.analytics_trend_wasted))
            }
        }
    }
}

@Composable
private fun TrendBar(value: Int, max: Int, maxHeight: androidx.compose.ui.unit.Dp, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (value > 0) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .width(14.dp)
                // Even zero gets a sliver so the week visibly exists.
                .height((maxHeight * value / max).coerceAtLeast(2.dp))
                .background(
                    color = if (value > 0) color else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                ),
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionList(title: String, rows: List<Pair<String, Int>>) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.spaceLg), verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { (label, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodChip(
    labelRes: Int,
    value: AnalyticsPeriod,
    current: AnalyticsPeriod,
    onSelect: (AnalyticsPeriod) -> Unit,
) {
    FilterChip(
        selected = value == current,
        onClick = { onSelect(value) },
        label = { Text(stringResource(labelRes)) },
    )
}
