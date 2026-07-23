package com.eatbefore.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eatbefore.core.security.SecretCipher
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App-wide theme selection. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * User settings backed by Preferences DataStore.
 *
 * The only secret here is the optional Open Food Facts password, and it is stored
 * encrypted via [com.eatbefore.core.security.SecretCipher] and deliberately kept out of
 * this data class so it never reaches UI state or a log.
 */
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
    /**
     * Automatic backup. The database lives only on this device (allowBackup=false), so
     * losing the phone loses everything unless copies land in a folder the user controls.
     */
    val autoBackupEnabled: Boolean = false,
    /** Tree URI of the user-picked folder (SAF), null until they choose one. */
    val autoBackupFolderUri: String? = null,
    val autoBackupKeepCount: Int = DEFAULT_BACKUP_KEEP_COUNT,
    /** Epoch millis of the last successful automatic backup, 0 when never run. */
    val lastAutoBackupAt: Long = 0,
    /**
     * Open Food Facts account name, null when the user has not linked one. Contributing a
     * product to the shared catalog requires an account — the API rejects anonymous
     * writes — so this doubles as "contributing is possible".
     */
    val offUsername: String? = null,
    /**
     * Folder shared with the other household member (SAF tree URI), null when sharing is
     * off. See docs/adr/0004-household-sharing.md.
     */
    val syncFolderUri: String? = null,
    /** Epoch millis of the last successful exchange, 0 when never run. */
    val lastSyncAt: Long = 0,
)

/** How many automatic copies to keep before deleting the oldest. */
const val DEFAULT_BACKUP_KEEP_COUNT = 7

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val secretCipher: SecretCipher,
) {
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
            autoBackupEnabled = prefs[KEY_AUTO_BACKUP] ?: false,
            autoBackupFolderUri = prefs[KEY_AUTO_BACKUP_FOLDER],
            autoBackupKeepCount = prefs[KEY_AUTO_BACKUP_KEEP] ?: DEFAULT_BACKUP_KEEP_COUNT,
            lastAutoBackupAt = prefs[KEY_LAST_AUTO_BACKUP] ?: 0L,
            offUsername = prefs[KEY_OFF_USERNAME]?.takeIf { it.isNotBlank() },
            syncFolderUri = prefs[KEY_SYNC_FOLDER]?.takeIf { it.isNotBlank() },
            lastSyncAt = prefs[KEY_LAST_SYNC] ?: 0L,
        )
    }

    /**
     * Links (or, with a blank username, unlinks) an Open Food Facts account. The password
     * is encrypted before it touches disk; a null [password] keeps the stored one so the
     * user can correct just the name.
     */
    suspend fun setOffAccount(username: String, password: String?) {
        val encrypted = password?.let(secretCipher::encrypt)
        dataStore.edit { prefs ->
            if (username.isBlank()) {
                prefs.remove(KEY_OFF_USERNAME)
                prefs.remove(KEY_OFF_PASSWORD)
                return@edit
            }
            prefs[KEY_OFF_USERNAME] = username.trim()
            if (encrypted != null) prefs[KEY_OFF_PASSWORD] = encrypted
        }
    }

    /**
     * Decrypted password for the linked account, or null when absent/undecryptable (for
     * example after a restore onto another device, where the Keystore key is gone).
     * Intentionally not part of [preferences] so it is never held in UI state.
     */
    suspend fun offPassword(): String? {
        val stored = dataStore.data.first()[KEY_OFF_PASSWORD] ?: return null
        return secretCipher.decrypt(stored)
    }

    /**
     * This installation's sync identity, created on first use and stable afterwards.
     * Random rather than derived from hardware ids, which are device-wide and would
     * link the user across apps.
     */
    suspend fun deviceId(): String {
        dataStore.data.first()[KEY_DEVICE_ID]?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = java.util.UUID.randomUUID().toString()
        // Concurrent callers must agree, so the write returns whatever landed first.
        return dataStore.edit { prefs ->
            if (prefs[KEY_DEVICE_ID].isNullOrBlank()) prefs[KEY_DEVICE_ID] = generated
        }[KEY_DEVICE_ID] ?: generated
    }

    /** Sync folder shared with the other household member (SAF tree URI). */
    suspend fun setSyncFolder(uri: String?) {
        dataStore.edit { prefs ->
            if (uri == null) prefs.remove(KEY_SYNC_FOLDER) else prefs[KEY_SYNC_FOLDER] = uri
        }
    }

    suspend fun setLastSyncAt(epochMillis: Long) {
        dataStore.edit { it[KEY_LAST_SYNC] = epochMillis }
    }

    /** Enabling requires a folder; the caller picks it via SAF first. */
    suspend fun setAutoBackup(enabled: Boolean, folderUri: String?) {
        dataStore.edit {
            it[KEY_AUTO_BACKUP] = enabled
            if (folderUri != null) it[KEY_AUTO_BACKUP_FOLDER] = folderUri
        }
    }

    suspend fun setLastAutoBackupAt(epochMillis: Long) {
        dataStore.edit { it[KEY_LAST_AUTO_BACKUP] = epochMillis }
    }

    /**
     * Applies a settings snapshot (from a backup). Deliberately restores only what the
     * user chose — not onboarding state, not the auto-backup folder (its SAF permission
     * belongs to the old device) and not the Open Food Facts account. Taking
     * [UserPreferences] rather than the backup DTO keeps this layer unaware of the file
     * format.
     */
    suspend fun restoreFrom(settings: UserPreferences) {
        dataStore.edit { prefs ->
            prefs[KEY_SOON_DAYS] = settings.soonThresholdDays.coerceIn(0, 60)
            prefs[KEY_DETAILED_QTY] = settings.detailedQuantityMode
            prefs[KEY_THEME_MODE] = settings.themeMode.name
            prefs[KEY_DYNAMIC_COLORS] = settings.dynamicColors
            prefs[KEY_NOTIF_ENABLED] = settings.notificationsEnabled
            prefs[KEY_NOTIF_HOUR] = settings.notificationHour.coerceIn(0, 23)
            prefs[KEY_NOTIF_MINUTE] = settings.notificationMinute.coerceIn(0, 59)
            prefs[KEY_QUIET_ENABLED] = settings.quietHoursEnabled
            prefs[KEY_QUIET_START] = settings.quietStartHour.coerceIn(0, 23)
            prefs[KEY_QUIET_END] = settings.quietEndHour.coerceIn(0, 23)
        }
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
        val KEY_AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")
        val KEY_AUTO_BACKUP_FOLDER = stringPreferencesKey("auto_backup_folder_uri")
        val KEY_AUTO_BACKUP_KEEP = intPreferencesKey("auto_backup_keep_count")
        val KEY_LAST_AUTO_BACKUP = longPreferencesKey("last_auto_backup_at")
        val KEY_OFF_USERNAME = stringPreferencesKey("off_username")
        val KEY_OFF_PASSWORD = stringPreferencesKey("off_password_encrypted")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_SYNC_FOLDER = stringPreferencesKey("sync_folder_uri")
        val KEY_LAST_SYNC = longPreferencesKey("last_sync_at")
    }
}
