package com.eatbefore.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.datastore.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface RootState {
    data object Loading : RootState
    data class Ready(val onboardingCompleted: Boolean) : RootState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
) : ViewModel() {
    val state: StateFlow<RootState> = preferences.preferences
        .map { RootState.Ready(it.onboardingCompleted) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RootState.Loading,
        )
}
