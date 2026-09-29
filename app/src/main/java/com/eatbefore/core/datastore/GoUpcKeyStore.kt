package com.eatbefore.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eatbefore.core.security.SecretCipher
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Why the Go-UPC key stopped giving answers, as last heard from the service. */
enum class CatalogKeyProblem {
    /** The service refused the key. */
    REJECTED,

    /** The month's requests are used up; lookups resume next month. */
    QUOTA,
}

/**
 * The Go-UPC key and what the service last said about it.
 *
 * Its own class, like [com.eatbefore.core.update.UpdatePreferences], rather than more
 * methods on [UserPreferencesRepository]: only the catalog and its settings row write
 * here. Reading for the settings screen stays in [UserPreferences] via the shared keys —
 * whether a key is saved, never the key itself.
 */
@Singleton
class GoUpcKeyStore @Inject constructor(private val dataStore: DataStore<Preferences>, private val secretCipher: SecretCipher) {

    /**
     * Stores the key encrypted, or with a blank [key] removes it. Either way the last
     * reported problem goes: it was about the old key. False when the key could not be
     * encrypted, in which case nothing is stored — keeping it in plain text instead is
     * not an option.
     */
    suspend fun setKey(key: String?): Boolean {
        val clean = key?.trim()?.takeIf { it.isNotEmpty() }
        val encrypted = clean?.let(secretCipher::encrypt)
        if (clean != null && encrypted == null) return false
        dataStore.edit { prefs ->
            prefs.remove(KEY_PROBLEM)
            if (encrypted == null) prefs.remove(KEY_SECRET) else prefs[KEY_SECRET] = encrypted
        }
        return true
    }

    /**
     * The decrypted key, or null when absent or undecryptable — the Keystore key it was
     * encrypted with does not survive reinstalling the app. Like
     * [UserPreferencesRepository.offPassword], never part of any UI state.
     */
    suspend fun key(): String? {
        val stored = dataStore.data.first()[KEY_SECRET] ?: return null
        return secretCipher.decrypt(stored)
    }

    /** Records what Go-UPC last said about the key; null once it answers normally again. */
    suspend fun setProblem(problem: CatalogKeyProblem?) {
        // Called on every lookup; writing only on change keeps the store from churning.
        if (dataStore.data.first()[KEY_PROBLEM] == problem?.name) return
        dataStore.edit { prefs ->
            if (problem == null) prefs.remove(KEY_PROBLEM) else prefs[KEY_PROBLEM] = problem.name
        }
    }

    /** Shared with [UserPreferencesRepository], which reads them for the settings screen. */
    companion object {
        val KEY_SECRET = stringPreferencesKey("go_upc_key_encrypted")
        val KEY_PROBLEM = stringPreferencesKey("go_upc_problem")
    }
}
