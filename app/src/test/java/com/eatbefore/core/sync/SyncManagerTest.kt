package com.eatbefore.core.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eatbefore.core.database.EatBeforeDatabase
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.core.diagnostics.DiagnosticsLog
import com.eatbefore.core.security.SecretCipher
import com.eatbefore.testutil.FakeAppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The half of sharing that touches the folder. `SyncEngine` — the merge rules — was well
 * covered, while this class, which decides what to read, what to write and what to leave
 * alone, was checked only by hand on two devices. That is how both v1.4.0 defects reached
 * users.
 *
 * A real temporary directory stands in for the SAF folder (see [SyncFolderResolver]). It
 * is not a cloud drive, so it cannot reproduce a half-synced file — but everything about
 * *which* files this code touches is exactly the same.
 */
@RunWith(RobolectricTestRunner::class)
class SyncManagerTest {

    private lateinit var context: Context
    private lateinit var folder: File
    private lateinit var db: EatBeforeDatabase
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var manager: SyncManager
    private lateinit var prefsFile: File
    private val prefsScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    private val clock = FakeAppClock()

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        folder = File(context.cacheDir, "shared-folder").apply {
            deleteRecursively()
            mkdirs()
        }
        db = Room.inMemoryDatabaseBuilder(context, EatBeforeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsFile = File(context.cacheDir, "sync-test.preferences_pb").apply { delete() }
        preferences = UserPreferencesRepository(
            dataStore = PreferenceDataStoreFactory.create(scope = prefsScope) { prefsFile },
            secretCipher = SecretCipher(),
        )
        preferences.setSyncFolder(folder.absolutePath)

        manager = SyncManager(
            appContext = context,
            engine = SyncEngine(db, clock),
            preferences = preferences,
            diagnostics = DiagnosticsLog(context, clock),
            folderResolver = { uri -> DocumentFile.fromFile(File(uri)) },
            deviceIdProvider = DeviceIdProvider(preferences),
            json = json,
            clock = clock,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        db.close()
        folder.deleteRecursively()
        prefsFile.delete()
        prefsScope.cancel()
    }

    /** The id is generated on first use and kept in preferences, so ask rather than assume. */
    private suspend fun ourJournalName(): String =
        SyncJournal.fileNameFor(DeviceIdProvider(preferences).deviceId())

    private fun writePeerJournal(
        deviceId: String,
        formatVersion: Int = SyncJournal.CURRENT_FORMAT_VERSION,
        deviceName: String = "",
    ) {
        val journal = SyncJournal(
            formatVersion = formatVersion,
            deviceId = deviceId,
            deviceName = deviceName,
            writtenAtEpochMillis = clock.now().toEpochMilli(),
        )
        File(folder, SyncJournal.fileNameFor(deviceId))
            .writeText(json.encodeToString(SyncJournal.serializer(), journal))
    }

    private fun files(): List<String> = folder.listFiles()?.map { it.name }?.sorted() ?: emptyList()

    @Test
    fun `an exchange publishes exactly one journal of our own`() = runTest {
        val result = manager.sync()

        assertTrue(result is SyncResult.Success)
        assertEquals(listOf(ourJournalName()), files())
    }

    @Test
    fun `syncing twice overwrites our journal instead of adding another`() = runTest {
        manager.sync()
        manager.sync()

        assertEquals(listOf(ourJournalName()), files())
    }

    /**
     * The defect found on two devices: a race between "sync now" and the periodic worker
     * left a second file behind. Only our own duplicates may be removed — a peer's file is
     * never ours to delete.
     */
    @Test
    fun `a duplicate of our own journal is cleaned up`() = runTest {
        manager.sync()
        val duplicate = File(folder, ourJournalName().replace(".json", " (1).json"))
        duplicate.writeText(File(folder, ourJournalName()).readText())

        manager.sync()

        assertEquals(listOf(ourJournalName()), files())
    }

    @Test
    fun `a peer journal is read and counted`() = runTest {
        writePeerJournal("device-b")

        val result = manager.sync()

        assertEquals(1, (result as SyncResult.Success).stats.peersSeen)
    }

    /**
     * "Who threw out the sour cream?" can only be answered if the name arrives with the
     * journal — the events themselves carry an id, and an id tells the user nothing.
     */
    @Test
    fun `a peer's name is remembered so its actions can be signed`() = runTest {
        writePeerJournal("device-b", deviceName = "Телефон Алексея")

        manager.sync()

        assertEquals(
            mapOf("device-b" to "Телефон Алексея"),
            preferences.preferences.first().peerNames,
        )
    }

    /** An older peer publishes no name at all; the history then says "another device". */
    @Test
    fun `a peer without a name leaves nothing behind`() = runTest {
        writePeerJournal("device-b")

        manager.sync()

        assertTrue(preferences.preferences.first().peerNames.isEmpty())
    }

    @Test
    fun `our own journal introduces this device by name`() = runTest {
        preferences.setDeviceName("Кухонный телефон")

        manager.sync()

        val published = json.decodeFromString(
            SyncJournal.serializer(),
            File(folder, ourJournalName()).readText(),
        )
        assertEquals("Кухонный телефон", published.deviceName)
    }

    @Test
    fun `a peer file is never deleted`() = runTest {
        writePeerJournal("device-b")

        manager.sync()

        assertTrue(SyncJournal.fileNameFor("device-b") in files())
    }

    /** Unrelated files in the shared folder must be left completely alone. */
    @Test
    fun `files that are not journals are ignored`() = runTest {
        File(folder, "shopping.txt").writeText("хлеб")
        File(folder, "eatbefore-2026-08-01.json").writeText("{}")

        val result = manager.sync()

        assertTrue(result is SyncResult.Success)
        assertTrue("shopping.txt" in files())
        assertTrue("eatbefore-2026-08-01.json" in files())
    }

    /**
     * A cloud client can expose a file mid-write. Skipping that peer and carrying on is
     * right; crashing, or refusing to publish our own state, is not.
     */
    @Test
    fun `a corrupt peer journal is skipped without failing the exchange`() = runTest {
        File(folder, SyncJournal.fileNameFor("device-b")).writeText("{ this is not json")

        val result = manager.sync()

        assertTrue(result is SyncResult.Success)
        assertEquals(0, (result as SyncResult.Success).stats.peersSeen)
        assertTrue("our journal must still be published", ourJournalName() in files())
    }

    /** A newer format may carry fields we would silently drop, so leave it to a newer app. */
    @Test
    fun `a journal from a newer format version is not merged`() = runTest {
        writePeerJournal("device-b", formatVersion = SyncJournal.CURRENT_FORMAT_VERSION + 1)

        val result = manager.sync()

        assertEquals(0, (result as SyncResult.Success).stats.peersSeen)
    }

    /** Our own file must never be merged back into us, whatever it is called. */
    @Test
    fun `our own journal is not treated as a peer`() = runTest {
        manager.sync()

        val result = manager.sync()

        assertEquals(0, (result as SyncResult.Success).stats.peersSeen)
    }

    @Test
    fun `without a chosen folder the exchange reports it rather than failing`() = runTest {
        preferences.setSyncFolder(null)

        assertEquals(SyncResult.NotConfigured, manager.sync())
    }

    /** A deleted folder or a revoked permission is the common real-world case. */
    @Test
    fun `a folder that is gone is reported as unavailable`() = runTest {
        folder.deleteRecursively()

        assertEquals(SyncResult.FolderUnavailable, manager.sync())
    }

    @Test
    fun `a successful exchange records when it happened`() = runTest {
        manager.sync()

        assertEquals(clock.now().toEpochMilli(), preferences.preferences.first().lastSyncAt)
    }
}
