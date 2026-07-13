package com.eatbefore.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** User settings backed by Preferences DataStore. No sensitive data is stored here. */
data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val soonThresholdDays: Int = DetermineExpiryStatusUseCase.DEFAULT_SOON_THRESHOLD_DAYS,
    val detailedQuantityMode: Boolean = false,
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            onboardingCompleted = prefs[KEY_ONBOARDING] ?: false,
            soonThresholdDays = prefs[KEY_SOON_DAYS]
                ?: DetermineExpiryStatusUseCase.DEFAULT_SOON_THRESHOLD_DAYS,
            detailedQuantityMode = prefs[KEY_DETAILED_QTY] ?: false,
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

    private companion object {
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val KEY_SOON_DAYS = intPreferencesKey("soon_threshold_days")
        val KEY_DETAILED_QTY = booleanPreferencesKey("detailed_quantity_mode")
    }
}
