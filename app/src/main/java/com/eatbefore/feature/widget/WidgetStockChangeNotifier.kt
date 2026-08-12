package com.eatbefore.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.eatbefore.core.diagnostics.DiagnosticsLog
import com.eatbefore.core.widget.StockChangeNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Redraws the home-screen widget as soon as stock changes, instead of leaving it to the
 * system's half-hourly refresh.
 *
 * Failures are swallowed on purpose, and only after being written down: a widget that
 * cannot be redrawn (none placed on the home screen, or the host misbehaving) must never
 * take down the write that just succeeded. Losing the milk you just recorded because its
 * widget could not repaint would be an absurd trade.
 */
@Singleton
class WidgetStockChangeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnostics: DiagnosticsLog,
) : StockChangeNotifier {

    override suspend fun onStockChanged() {
        runCatching { ExpiringWidget().updateAll(context) }
            .onFailure { diagnostics.record("WIDGET", "Could not refresh the widget", it) }
    }
}
