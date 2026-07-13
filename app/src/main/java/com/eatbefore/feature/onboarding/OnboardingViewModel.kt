package com.eatbefore.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.datastore.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    /** Persists that onboarding is done; the root observer then routes to Home. */
    fun complete() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
        }
    }
}
