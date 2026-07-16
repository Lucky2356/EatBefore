package com.eatbefore.feature.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.core.common.validation.InputValidator
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType
import com.eatbefore.domain.repository.StorageLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manage storage locations: add custom ones, rename, set default, archive. Guards:
 * the default location and the last active one cannot be archived.
 */
@HiltViewModel
class LocationsViewModel @Inject constructor(private val repository: StorageLocationRepository) : ViewModel() {

    val locations: StateFlow<List<StorageLocation>> = repository.observeActive().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun add(name: String, type: StorageType) {
        val clean = InputValidator.sanitizeText(name, MAX_LOCATION_NAME) ?: return
        viewModelScope.launch {
            val sortOrder = (locations.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
            repository.upsert(
                StorageLocation(name = clean, type = type, sortOrder = sortOrder),
            )
        }
    }

    fun rename(location: StorageLocation, newName: String) {
        val clean = InputValidator.sanitizeText(newName, MAX_LOCATION_NAME) ?: return
        viewModelScope.launch { repository.upsert(location.copy(name = clean)) }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch { repository.setDefault(id) }
    }

    /** Archive hides the location from pickers; batches stored there stay intact. */
    fun archive(location: StorageLocation) {
        if (!canArchive(location)) return
        viewModelScope.launch { repository.upsert(location.copy(isArchived = true)) }
    }

    fun canArchive(location: StorageLocation): Boolean = !location.isDefault && locations.value.size > 1

    private companion object {
        const val MAX_LOCATION_NAME = 60
    }
}
