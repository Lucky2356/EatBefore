package com.eatbefore.core.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.eatbefore.core.common.dispatcher.IoDispatcher
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.core.diagnostics.DiagnosticsLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Why an exchange could not run. Each maps to a specific, actionable message. */
sealed interface SyncResult {
    data class Success(val stats: SyncStats) : SyncResult

    /** No shared folder chosen yet. */
    data object NotConfigured : SyncResult

    /** The folder is gone or the permission was revoked. */
    data object FolderUnavailable : SyncResult

    data class Failed(val message: String) : SyncResult
}

/**
 * Exchanges household journals through a folder both phones can reach — typically a
 * synced cloud folder picked via SAF.
 *
 * Deliberately **not** a server: the app has no accounts and no backend, and adding one
 * for this would undo the local-first promise (ADR-0004). The cost is that changes appear
 * when both devices have opened the app and the drive has synced, not instantly.
 *
 * Each device owns exactly one file, `journal-<deviceId>.json`, and never writes to
 * anyone else's. Two phones writing simultaneously therefore cannot corrupt a shared
 * file — there is no cross-device locking available over a cloud drive.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val engine: SyncEngine,
    private val preferences: UserPreferencesRepository,
    private val diagnostics: DiagnosticsLog,
    private val deviceIdProvider: DeviceIdProvider,
    private val json: Json,
    private val clock: AppClock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Only one exchange at a time. Tapping "sync now" while the periodic worker is
     * already running used to let both write the journal at once: each checked for an
     * existing file, neither found one yet, and SAF happily created a second
     * "journal-<id> (1).json". Peers then read the stale copy as if it were another
     * household member.
     */
    private val mutex = Mutex()

    suspend fun sync(): SyncResult = mutex.withLock {
        withContext(ioDispatcher) { syncInternal() }
    }

    private suspend fun syncInternal(): SyncResult {
        val folderUri = preferences.preferences.first().syncFolderUri
            ?: return SyncResult.NotConfigured
        val folder = DocumentFile.fromTreeUri(appContext, Uri.parse(folderUri))
        if (folder == null || !folder.canWrite()) return SyncResult.FolderUnavailable

        val deviceId = deviceIdProvider.deviceId()

        return try {
            // Read peers first: publishing our own state afterwards means a partial read
            // never leaves the folder advertising data we have not merged.
            var total = SyncStats()
            folder.listFiles()
                .filter { it.isJournal() && it.name != SyncJournal.fileNameFor(deviceId) }
                .forEach { file ->
                    val journal = readJournal(file) ?: return@forEach
                    // A newer format may carry fields we would silently drop.
                    if (journal.formatVersion > SyncJournal.CURRENT_FORMAT_VERSION) return@forEach
                    if (journal.deviceId == deviceId) return@forEach
                    val stats = engine.merge(journal)
                    total = total.plus(stats)
                }

            writeOwnJournal(folder, deviceId)
            preferences.setLastSyncAt(clock.now().toEpochMilli())
            SyncResult.Success(total)
        } catch (e: Exception) {
            // A half-synced cloud file is normal, not a crash-worthy condition — but the
            // user only sees "exchange failed", so leave something to look at afterwards.
            diagnostics.record("SYNC", "Exchange did not finish", e)
            SyncResult.Failed(e.message ?: "Sync failed")
        }
    }

    private fun DocumentFile.isJournal(): Boolean {
        val name = name ?: return false
        return isFile && name.startsWith(SyncJournal.FILE_PREFIX) && name.endsWith(SyncJournal.FILE_SUFFIX)
    }

    private fun readJournal(file: DocumentFile): SyncJournal? = runCatching {
        appContext.contentResolver.openInputStream(file.uri)?.use { stream ->
            json.decodeFromString(SyncJournal.serializer(), stream.readBytes().toString(Charsets.UTF_8))
        }
    }.onFailure { error ->
        // Skipping a peer is the one failure that looks like success: the exchange reports
        // "done" having merged nothing. The file name is safe to log; its contents are not.
        diagnostics.record("SYNC", "Could not read peer journal ${file.name}", error)
    }.getOrNull()

    private suspend fun writeOwnJournal(folder: DocumentFile, deviceId: String) {
        val name = SyncJournal.fileNameFor(deviceId)
        val content = json.encodeToString(
            SyncJournal.serializer(),
            engine.buildOwnJournal(deviceId),
        )
        // Overwrite in place when possible: recreating the file changes its identity and
        // some cloud clients treat that as a delete plus an add.
        val ours = folder.listFiles().filter { it.isOurJournal(deviceId) }
        val target = ours.firstOrNull { it.name == name }
            ?: ours.firstOrNull()
            ?: folder.createFile(MIME_TYPE, name)
            ?: return
        appContext.contentResolver.openOutputStream(target.uri, "wt")?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        }

        // Tidy up "journal-<id> (1).json" copies left by the race fixed above, or by a
        // cloud client resolving a conflict. Only ever our own files.
        ours.filter { it.uri != target.uri }.forEach { runCatching { it.delete() } }
    }

    /** Our own journal, including duplicates a cloud client may have made. */
    private fun DocumentFile.isOurJournal(deviceId: String): Boolean {
        val name = name ?: return false
        return isFile &&
            name.startsWith("${SyncJournal.FILE_PREFIX}$deviceId") &&
            name.endsWith(SyncJournal.FILE_SUFFIX)
    }

    private fun SyncStats.plus(other: SyncStats) = SyncStats(
        peersSeen = peersSeen + other.peersSeen,
        productsAdded = productsAdded + other.productsAdded,
        batchesAdded = batchesAdded + other.batchesAdded,
        batchesUpdated = batchesUpdated + other.batchesUpdated,
        eventsAdded = eventsAdded + other.eventsAdded,
    )

    private companion object {
        const val MIME_TYPE = "application/json"
    }
}
