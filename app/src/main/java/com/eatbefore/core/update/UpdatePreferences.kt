package com.eatbefore.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes down what the update check found.
 *
 * A class of its own rather than three more methods on [UserPreferencesRepository]: that
 * one already serves every screen in the app, and update bookkeeping is written by exactly
 * two callers. Reading stays there — the settings screens want one state object, not one
 * per feature — so the keys are shared deliberately.
 */
@Singleton
class UpdatePreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    /**
     * Remembers what the last check saw. A null [version] means "nothing newer" and clears
     * the mark — otherwise it would keep pointing at a release already installed.
     */
    suspend fun record(version: String?, at: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CHECK] = at
            if (version == null) {
                prefs.remove(KEY_AVAILABLE)
            } else {
                prefs[KEY_AVAILABLE] = version
            }
        }
    }

    /**
     * The keys live here, with the only code that writes them; the settings repository
     * reads them by reference rather than declaring its own copies, because two spellings
     * of one key is a setting that silently stops being remembered.
     */
    companion object {
        val KEY_ENABLED = booleanPreferencesKey("update_check_enabled")
        val KEY_LAST_CHECK = longPreferencesKey("last_update_check_at")
        val KEY_AVAILABLE = stringPreferencesKey("available_update_version")
    }
}
