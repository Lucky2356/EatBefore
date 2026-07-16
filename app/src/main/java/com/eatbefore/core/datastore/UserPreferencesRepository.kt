package com.eatbefore.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App-wide theme selection. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User settings backed by Preferences DataStore. No sensitive data is stored here. */
data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val soonThresholdDays: Int = DetermineExpiryStatusUseCase.DEFAULT_SOON_THRESHOLD_DAYS,
    val detailedQuantityMode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Material You palette from the wallpaper (Android 12+). Off by default so the app
     * shows its own food-themed green identity; users can opt into Material You.
     */
    val dynamicColors: Boolean = false,
    val notificationsEnabled: Boolean = true,
    /** Local time of day for the daily expiry check (24h). */
    val notificationHour: Int = 9,
    val notificationMinute: Int = 0,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 8,
)

@Singleton
class UserPreferencesRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            onboardingCompleted = prefs[KEY_ONBOARDING] ?: false,
            soonThresholdDays = prefs[KEY_SOON_DAYS]
                ?: DetermineExpiryStatusUseCase.DEFAULT_SOON_THRESHOLD_DAYS,
            detailedQuantityMode = prefs[KEY_DETAILED_QTY] ?: false,
            themeMode = prefs[KEY_THEME_MODE]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            dynamicColors = prefs[KEY_DYNAMIC_COLORS] ?: false,
            notificationsEnabled = prefs[KEY_NOTIF_ENABLED] ?: true,
            notificationHour = prefs[KEY_NOTIF_HOUR] ?: 9,
            notificationMinute = prefs[KEY_NOTIF_MINUTE] ?: 0,
            quietHoursEnabled = prefs[KEY_QUIET_ENABLED] ?: false,
            quietStartHour = prefs[KEY_QUIET_START] ?: 22,
            quietEndHour = prefs[KEY_QUIET_END] ?: 8,
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING] = completed }
    }

    suspend fun setSoonThresholdDays(days: Int) {
        dataStore.edit { it[KEY_SOON_DAYS] = days.coerceIn(0, 60) }
    }

    suspend fun setDetailedQuantityMode(enabled: Boolean) {
        dataStore.edit { it[KEY_DETAILED_QTY] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLORS] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_ENABLED] = enabled }
    }

    suspend fun setNotificationTime(hour: Int, minute: Int) {
        dataStore.edit {
            it[KEY_NOTIF_HOUR] = hour.coerceIn(0, 23)
            it[KEY_NOTIF_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setQuietHours(enabled: Boolean, startHour: Int, endHour: Int) {
        dataStore.edit {
            it[KEY_QUIET_ENABLED] = enabled
            it[KEY_QUIET_START] = startHour.coerceIn(0, 23)
            it[KEY_QUIET_END] = endHour.coerceIn(0, 23)
        }
    }

    private companion object {
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val KEY_SOON_DAYS = intPreferencesKey("soon_threshold_days")
        val KEY_DETAILED_QTY = booleanPreferencesKey("detailed_quantity_mode")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val KEY_NOTIF_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_NOTIF_HOUR = intPreferencesKey("notification_hour")
        val KEY_NOTIF_MINUTE = intPreferencesKey("notification_minute")
        val KEY_QUIET_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val KEY_QUIET_START = intPreferencesKey("quiet_start_hour")
        val KEY_QUIET_END = intPreferencesKey("quiet_end_hour")
    }
}
