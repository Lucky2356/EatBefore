package com.eatbefore.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.eatbefore.MainActivity
import com.eatbefore.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.temporal.ChronoUnit

/**
 * Home-screen widget listing what expires soonest, so the app does its job without being
 * opened. Data is read on each update via a Hilt entry point — Glance receivers are
 * constructed by the system, so constructor injection is not available.
 */
class ExpiringWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetDependencies {
        fun inventoryRepository(): com.eatbefore.domain.repository.InventoryRepository
        fun preferences(): com.eatbefore.core.datastore.UserPreferencesRepository
        fun clock(): com.eatbefore.core.common.time.AppClock
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDependencies::class.java,
        )
        val clock = deps.clock()
        val prefs = deps.preferences().preferences.first()
        val today = clock.today()
        val threshold = today.plusDays(prefs.soonThresholdDays.toLong()).toEpochDay()

        val rows = deps.inventoryRepository()
            .observeExpiringBefore(threshold)
            .first()
            .take(MAX_ROWS)
            .map { item ->
                val date = item.batch.effectiveExpirationDate
                WidgetRow(
                    name = item.product.name,
                    daysLeft = date?.let { ChronoUnit.DAYS.between(today, it) },
                )
            }

        provideContent {
            GlanceTheme {
                WidgetContent(context, rows)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context, rows: List<WidgetRow>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(
                text = context.getString(R.string.widget_title),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                ),
            )
            Spacer(modifier = GlanceModifier.padding(4.dp))

            if (rows.isEmpty()) {
                Text(
                    text = context.getString(R.string.widget_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            } else {
                rows.forEach { row ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.name,
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = row.daysLabel(context),
                            style = TextStyle(color = GlanceTheme.colors.primary),
                        )
                    }
                }
            }
        }
    }

    private data class WidgetRow(val name: String, val daysLeft: Long?) {
        fun daysLabel(context: Context): String = when {
            daysLeft == null -> ""
            daysLeft < 0 -> context.getString(R.string.widget_expired)
            daysLeft == 0L -> context.getString(R.string.widget_today)
            else -> context.resources.getQuantityString(
                R.plurals.widget_days_left,
                daysLeft.toInt(),
                daysLeft.toInt(),
            )
        }
    }

    private companion object {
        const val MAX_ROWS = 5
    }
}

/** System entry point for the widget; declared in the manifest. */
class ExpiringWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpiringWidget()
}
