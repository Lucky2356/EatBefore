package com.eatbefore.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.R
import com.eatbefore.core.backup.BackupManager
import com.eatbefore.core.common.dispatcher.IoDispatcher
import com.eatbefore.core.datastore.UserPreferences
import com.eatbefore.core.datastore.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val backupManager: BackupManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<UserPreferences> = preferences.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences(),
    )

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

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

    /** Writes a backup to the user-chosen document. Explicit user action only. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val content = backupManager.export()
                    context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open output")
                }
            }
            _message.value =
                if (result.isSuccess) R.string.backup_export_done else R.string.backup_export_error
        }
    }

    /** Restores from a user-chosen document; caller confirms the overwrite beforehand. */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                        readLimited(stream)
                    } ?: error("Cannot open input")
                    backupManager.import(content)
                }
            }
            _message.value =
                if (result.isSuccess) R.string.backup_import_done else R.string.backup_import_error
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Reads at most [MAX_IMPORT_BYTES] so a huge or malicious file can't exhaust memory. */
    private fun readLimited(stream: java.io.InputStream): String {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            total += read
            require(total <= MAX_IMPORT_BYTES) { "Backup file is too large" }
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 20L * 1024 * 1024
    }
}
