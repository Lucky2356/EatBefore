package com.eatbefore.core.widget

/**
 * Told whenever stock changes, so surfaces outside the app can catch up.
 *
 * There is exactly one such surface today: the home-screen widget. It reads the database
 * when the system decides to refresh it, which by its own configuration is every half an
 * hour — so writing off the milk in the app left the widget claiming for up to thirty
 * minutes that it still expires today. A widget that lies is worse than no widget: it is
 * the one place the user looks *without* opening the app.
 *
 * An interface rather than a direct call so the data layer does not have to know that
 * widgets, Glance or Android UI exist at all.
 */
fun interface StockChangeNotifier {
    suspend fun onStockChanged()
}
