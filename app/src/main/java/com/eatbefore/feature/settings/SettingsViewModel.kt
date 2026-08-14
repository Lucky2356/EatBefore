package com.eatbefore.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.R
import com.eatbefore.core.backup.AutoBackupCatalog
import com.eatbefore.core.backup.AutoBackupEntry
import com.eatbefore.core.backup.BackupManager
import com.eatbefore.core.common.dispatcher.IoDispatcher
import com.eatbefore.core.datastore.ThemeMode
import com.eatbefore.core.datastore.UserPreferences
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.core.diagnostics.DiagnosticsLog
import com.eatbefore.core.sync.SyncManager
import com.eatbefore.core.sync.SyncResult
import com.eatbefore.core.sync.SyncScheduler
import com.eatbefore.domain.catalog.CatalogContributor
import com.eatbefore.domain.catalog.ContributionResult
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.repository.StorageLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val storageLocations: StorageLocationRepository,
    private val backupManager: BackupManager,
    private val autoBackupCatalog: AutoBackupCatalog,
    private val diagnostics: DiagnosticsLog,
    private val syncManager: SyncManager,
    private val syncScheduler: SyncScheduler,
    private val catalogContributor: CatalogContributor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<UserPreferences> = preferences.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences(),
    )

    /** Active storage locations; the default one is the target of quick adds. */
    val locations: StateFlow<List<StorageLocation>> = storageLocations.observeActive().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /**
     * Copies the automatic backup has written, newest first. Loaded on demand — the folder
     * lives behind SAF and listing it is a real filesystem call, not something to repeat
     * every time the settings screen recomposes.
     */
    private val _autoBackups = MutableStateFlow<List<AutoBackupEntry>?>(null)
    val autoBackups: StateFlow<List<AutoBackupEntry>?> = _autoBackups.asStateFlow()

    fun loadAutoBackups() {
        viewModelScope.launch { _autoBackups.value = autoBackupCatalog.list() }
    }

    fun clearAutoBackups() {
        _autoBackups.value = null
    }

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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    /** Links or, with a blank [username], unlinks the Open Food Facts account. */
    fun setOffAccount(username: String, password: String?) {
        viewModelScope.launch {
            preferences.setOffAccount(username, password)
            _message.value = if (username.isBlank()) {
                R.string.settings_off_removed
            } else {
                R.string.settings_off_saved
            }
            refreshCatalogAccountUsable()
        }
    }

    /**
     * Whether the stored account is usable at all — the username is kept in plain
     * preferences, but the password is encrypted with a key that does not survive
     * reinstalling the app. Settings used to say "linked" purely from the username, so an
     * account whose password could no longer be read looked perfectly fine while quietly
     * doing nothing.
     */
    private val _catalogAccountUsable = MutableStateFlow(true)
    val catalogAccountUsable: StateFlow<Boolean> = _catalogAccountUsable.asStateFlow()

    private val _isCheckingCatalog = MutableStateFlow(false)
    val isCheckingCatalog: StateFlow<Boolean> = _isCheckingCatalog.asStateFlow()

    init {
        viewModelScope.launch { refreshCatalogAccountUsable() }
    }

    private suspend fun refreshCatalogAccountUsable() {
        _catalogAccountUsable.value = catalogContributor.isConfigured()
    }

    /** Asks the catalog whether the account works, and says so plainly either way. */
    fun checkCatalogAccount() {
        if (_isCheckingCatalog.value) return
        _isCheckingCatalog.value = true
        viewModelScope.launch {
            val result = catalogContributor.checkAccount()
            refreshCatalogAccountUsable()
            _isCheckingCatalog.value = false
            _message.value = when (result) {
                ContributionResult.Success -> R.string.settings_off_check_ok
                ContributionResult.AuthFailed -> R.string.settings_off_check_auth_failed
                ContributionResult.NotConfigured -> R.string.settings_off_check_not_configured
                is ContributionResult.Failed -> R.string.settings_off_check_failed
            }
        }
    }

    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColors(enabled) }
    }

    fun setDetailedQuantityMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setDetailedQuantityMode(enabled) }
    }

    fun setDefaultLocation(id: Long) {
        viewModelScope.launch { storageLocations.setDefault(id) }
    }

    /**
     * Turns automatic backup on with the folder the user just granted access to. The
     * permission must outlive this process, hence takePersistableUriPermission.
     */
    fun enableAutoBackup(folderUri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    folderUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            preferences.setAutoBackup(enabled = true, folderUri = folderUri.toString())
            _message.value = R.string.settings_auto_backup_on
        }
    }

    fun disableAutoBackup() {
        viewModelScope.launch { preferences.setAutoBackup(enabled = false, folderUri = null) }
    }

    /**
     * Turns on household sharing with the folder the user just picked, and syncs at once
     * so they can see whether it worked instead of waiting for the periodic job.
     */
    fun enableSharing(folderUri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    folderUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            preferences.setSyncFolder(folderUri.toString())
            syncScheduler.apply(preferences.preferences.first())
            syncNow()
        }
    }

    fun disableSharing() {
        viewModelScope.launch {
            preferences.setSyncFolder(null)
            syncScheduler.apply(preferences.preferences.first())
            _message.value = R.string.settings_sharing_off
        }
    }

    /**
     * Renames this phone for the other household member. The name only reaches them with
     * the next exchange — the journal carries it — so nothing is announced here.
     */
    fun setDeviceName(name: String) {
        viewModelScope.launch { preferences.setDeviceName(name) }
    }

    /** Exchanges journals right now. Shown as a spinner so a slow drive is visible. */
    fun syncNow() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        viewModelScope.launch {
            _message.value = when (val result = syncManager.sync()) {
                is SyncResult.Success -> if (result.stats.peersSeen == 0) {
                    R.string.settings_sharing_no_peers
                } else {
                    R.string.settings_sharing_done
                }

                SyncResult.NotConfigured -> R.string.settings_sharing_not_configured
                SyncResult.FolderUnavailable -> R.string.settings_sharing_folder_gone
                is SyncResult.Failed -> R.string.settings_sharing_failed
            }
            _isSyncing.value = false
        }
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
    fun importFrom(uri: Uri, mode: BackupManager.ImportMode) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                        readLimited(stream)
                    } ?: error("Cannot open input")
                    // Import rewrites everything; keep an escape hatch on disk first.
                    saveSafetyCopy()
                    backupManager.import(content, mode)
                }
            }
            _message.value =
                if (result.isSuccess) R.string.backup_import_done else R.string.backup_import_error
        }
    }

    /**
     * Writes the current data to app storage before a destructive import, so a mistaken
     * import is recoverable even though the user has no copy of their own.
     */
    private suspend fun saveSafetyCopy() {
        runCatching {
            val dir = java.io.File(context.filesDir, SAFETY_DIR).apply { mkdirs() }
            java.io.File(dir, SAFETY_FILE).writeText(backupManager.export())
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * The diagnostics log as shareable text, or null when nothing has gone wrong. Read on
     * demand rather than observed: it changes only on failures, and polling a file to keep
     * a settings row up to date would cost more than it tells anyone.
     */
    fun diagnosticsReport(): String? = diagnostics.report()

    fun showDiagnosticsEmpty() {
        _message.value = R.string.settings_diagnostics_empty
    }

    fun clearDiagnostics() {
        diagnostics.clear()
        _message.value = R.string.settings_diagnostics_cleared
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
        const val SAFETY_DIR = "safety"
        const val SAFETY_FILE = "before-import.json"
    }
}
