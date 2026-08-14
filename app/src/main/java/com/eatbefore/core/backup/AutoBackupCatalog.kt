package com.eatbefore.core.backup

import android.net.Uri
import com.eatbefore.core.common.dispatcher.IoDispatcher
import com.eatbefore.core.common.storage.FolderResolver
import com.eatbefore.core.datastore.UserPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** One automatic copy: what it is called, when it was written, and how to read it. */
data class AutoBackupEntry(val name: String, val uri: Uri, val writtenAtEpochMillis: Long)

/**
 * Lists the copies the automatic backup has already written.
 *
 * The copies existed from the start; reaching them did not. Restoring meant «Импорт из
 * файла» and hunting through the system file picker for a folder chosen months earlier —
 * which is the difference between having insurance and being able to claim on it, and the
 * moment it matters is exactly the moment the user's hands are shaking.
 *
 * Only files this app generated are offered ([AutoBackupWorker] names them), so the list
 * cannot fill up with unrelated JSON the user happens to keep in the same folder.
 */
@Singleton
class AutoBackupCatalog @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val folderResolver: FolderResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Newest first. Empty when no folder is configured or it is no longer reachable. */
    suspend fun list(): List<AutoBackupEntry> = withContext(ioDispatcher) {
        val folderUri = preferences.preferences.first().autoBackupFolderUri
            ?: return@withContext emptyList()
        val folder = runCatching { folderResolver.resolve(folderUri) }.getOrNull()
            ?: return@withContext emptyList()
        if (!folder.canRead()) return@withContext emptyList()

        runCatching {
            folder.listFiles()
                .filter { it.isFile && it.name?.startsWith(FILE_PREFIX) == true && it.name?.endsWith(SUFFIX) == true }
                .map { AutoBackupEntry(it.name.orEmpty(), it.uri, it.lastModified()) }
                // By name, not by lastModified: cloud clients rewrite the timestamp when
                // they re-download a file, and the name carries the moment it was taken.
                .sortedByDescending { it.name }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val FILE_PREFIX = "eatbefore-"
        const val SUFFIX = ".json"
    }
}
