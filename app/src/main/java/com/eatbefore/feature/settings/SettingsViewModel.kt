package com.eatbefore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.datastore.UserPreferences
import com.eatbefore.core.datastore.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<UserPreferences> = preferences.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences(),
    )

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setNotificationsEnabled(enabled) }
    }

    fun setNotificationTime(hour: Int, minute: Int) {
        viewModelScope.launch { preferences.setNotificationTime(hour, minute) }
    }

    fun setSoonDays(days: Int) {
        viewModelScope.launch { preferences.setSoonThresholdDays(days) }
    }

    fun setQuietHours(enabled: Boolean, startHour: Int, endHour: Int) {
        viewModelScope.launch { preferences.setQuietHours(enabled, startHour, endHour) }
    }
}
