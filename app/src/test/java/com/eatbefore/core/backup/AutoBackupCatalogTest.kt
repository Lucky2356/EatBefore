package com.eatbefore.core.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.core.security.SecretCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Restoring is the one feature nobody exercises until something has already gone wrong, so
 * the list it offers has to be right the first time it is opened. A real temporary
 * directory stands in for the SAF folder (see
 * [com.eatbefore.core.common.storage.FolderResolver]).
 *
 * `runBlocking` rather than `runTest`: there is no virtual time to advance here, and
 * `runTest` additionally fails on coroutine exceptions raised anywhere earlier in the JVM
 * — Room's invalidation tracker can refresh after another test class closed its database,
 * and that failure would then be reported against this class, which never touches Room.
 */
@RunWith(RobolectricTestRunner::class)
class AutoBackupCatalogTest {

    private lateinit var context: Context
    private lateinit var folder: File
    private lateinit var prefsFile: File
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var catalog: AutoBackupCatalog
    private val prefsScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        folder = File(context.cacheDir, "backup-folder").apply {
            deleteRecursively()
            mkdirs()
        }
        // A name per test: DataStore keeps a process-wide registry of the files it has
        // open, and cancelling the previous scope frees the entry asynchronously — reusing
        // one name makes the *next* test fail for something the previous one did.
        prefsFile = File(context.cacheDir, "backup-test-${System.nanoTime()}.preferences_pb")
        preferences = UserPreferencesRepository(
            dataStore = PreferenceDataStoreFactory.create(scope = prefsScope) { prefsFile },
            secretCipher = SecretCipher(),
        )
        catalog = AutoBackupCatalog(
            preferences = preferences,
            folderResolver = { uri -> DocumentFile.fromFile(File(uri)) },
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        folder.deleteRecursively()
        prefsFile.delete()
        prefsScope.cancel()
    }

    private suspend fun useFolder() =
        preferences.setAutoBackup(enabled = true, folderUri = folder.absolutePath)

    private fun write(name: String) = File(folder, name).writeText("{}")

    @Test
    fun `the copies are listed newest first`() = runBlocking {
        useFolder()
        write("eatbefore-2026-08-01-0300.json")
        write("eatbefore-2026-08-14-0300.json")
        write("eatbefore-2026-08-07-0300.json")

        assertEquals(
            listOf(
                "eatbefore-2026-08-14-0300.json",
                "eatbefore-2026-08-07-0300.json",
                "eatbefore-2026-08-01-0300.json",
            ),
            catalog.list().map { it.name },
        )
    }

    /**
     * The folder is the user's own and may hold anything. Offering to restore from a file
     * this app never wrote would end in "not a valid backup file" at best.
     */
    @Test
    fun `files this app did not write are not offered`() = runBlocking {
        useFolder()
        write("eatbefore-2026-08-14-0300.json")
        write("shopping.json")
        write("eatbefore-notes.txt")

        assertEquals(listOf("eatbefore-2026-08-14-0300.json"), catalog.list().map { it.name })
    }

    @Test
    fun `no folder chosen means nothing to offer`() = runBlocking {
        assertTrue(catalog.list().isEmpty())
    }

    /** A folder can be deleted, or its permission revoked, months after it was chosen. */
    @Test
    fun `a folder that is gone is empty rather than a failure`() = runBlocking {
        useFolder()
        write("eatbefore-2026-08-14-0300.json")
        folder.deleteRecursively()

        assertTrue(catalog.list().isEmpty())
    }
}
