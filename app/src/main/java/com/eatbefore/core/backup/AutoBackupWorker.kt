package com.eatbefore.core.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.datastore.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter

/**
 * Writes a dated backup into the user-chosen folder and prunes old ones.
 *
 * The database is local-only (`allowBackup=false`), so without this a lost or wiped
 * phone loses the whole history. Scheduled by [AutoBackupScheduler]; failures retry
 * rather than surface, since the user is not present when this runs.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val preferences: UserPreferencesRepository,
    private val clock: AppClock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = preferences.preferences.first()
        if (!prefs.autoBackupEnabled) return Result.success()
        val folderUri = prefs.autoBackupFolderUri?.let(Uri::parse) ?: return Result.success()

        return runCatching {
            val folder = DocumentFile.fromTreeUri(appContext, folderUri)
            // The folder can be deleted or the permission revoked long after setup.
            if (folder == null || !folder.canWrite()) return Result.failure()

            val stamp = clock.now().atZone(clock.zone()).format(FILE_STAMP)
            val name = "eatbefore-$stamp.json"
            val file = folder.createFile(MIME_TYPE, name) ?: return Result.retry()

            val content = backupManager.export()
            appContext.contentResolver.openOutputStream(file.uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            } ?: return Result.retry()

            pruneOldBackups(folder, prefs.autoBackupKeepCount)
            preferences.setLastAutoBackupAt(clock.now().toEpochMilli())
            Result.success()
        }.getOrElse { Result.retry() }
    }

    /** Keeps the newest [keep] generated files; user-named exports are never touched. */
    private fun pruneOldBackups(folder: DocumentFile, keep: Int) {
        folder.listFiles()
            .filter { it.name?.startsWith(FILE_PREFIX) == true && it.name?.endsWith(".json") == true }
            .sortedByDescending { it.name }
            .drop(keep)
            .forEach { runCatching { it.delete() } }
    }

    companion object {
        const val WORK_NAME = "auto_backup_daily"
        private const val MIME_TYPE = "application/json"
        private const val FILE_PREFIX = "eatbefore-"

        // Sorts lexicographically by time, which pruning relies on.
        private val FILE_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
    }
}
