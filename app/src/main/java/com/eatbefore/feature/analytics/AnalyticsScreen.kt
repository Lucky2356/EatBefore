package com.eatbefore.feature.analytics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.EmptyState
import com.eatbefore.core.designsystem.theme.LocalStatusColors
import com.eatbefore.domain.usecase.AnalyticsSummary
import com.eatbefore.domain.usecase.BuildAnalyticsUseCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analytics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            }
        }
    }
}

@Composable
private fun SummaryContent(summary: AnalyticsSummary) {
    val statusColors = LocalStatusColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile(
            label = stringResource(R.string.analytics_added),
            value = summary.addedCount,
            color = MaterialTheme.colorScheme.primaryContainer,
            onColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.analytics_consumed),
            value = summary.consumedCount,
            color = MaterialTheme.colorScheme.secondaryContainer,
            onColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile(
            label = stringResource(R.string.analytics_discarded),
            value = summary.discardedCount,
            color = MaterialTheme.colorScheme.errorContainer,
            onColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.analytics_expired),
            value = summary.expiredCount,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            onColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
    }

    summary.usedInTimePercent?.let { percent ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.analytics_used_in_time, percent),
                    style = MaterialTheme.typography.titleMedium,
                )
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = statusColors.fresh,
                )
            }
        }
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

@Composable
private fun StatTile(
    label: String,
    value: Int,
    color: Color,
    onColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = onColor,
                textAlign = TextAlign.Start,
            )
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = onColor)
        }
    }
}

@Composable
private fun SectionList(title: String, rows: List<Pair<String, Int>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
