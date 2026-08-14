package com.eatbefore.feature.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.eatbefore.R
import com.eatbefore.core.backup.AutoBackupCatalog
import com.eatbefore.core.backup.AutoBackupEntry
import com.eatbefore.core.backup.BackupManager
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.core.diagnostics.DiagnosticsLog
import com.eatbefore.core.security.SecretCipher
import com.eatbefore.core.sync.SyncManager
import com.eatbefore.core.sync.SyncResult
import com.eatbefore.core.sync.SyncScheduler
import com.eatbefore.core.sync.SyncStats
import com.eatbefore.domain.catalog.CatalogContributor
import com.eatbefore.domain.catalog.ContributionResult
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.FakeStorageLocationRepository
import com.eatbefore.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Settings is where the destructive buttons live: it replaces the whole database on
 * import, hands folders to a background writer, and holds the catalog password. It was the
 * largest ViewModel in the app with no tests at all — every one of those paths was checked
 * only by pressing it and looking.
 *
 * A real [UserPreferencesRepository] over a throwaway DataStore file, because "the setting
 * was actually stored" is half of what these methods promise; the rest is mocked, since
 * exporting a database or reaching a cloud folder is not this class's job to prove.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var prefsFile: File
    private lateinit var preferences: UserPreferencesRepository
    private val prefsScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    private val backupManager = mockk<BackupManager>(relaxed = true)
    private val autoBackupCatalog = mockk<AutoBackupCatalog>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)
    private val syncScheduler = mockk<SyncScheduler>(relaxed = true)
    private val catalogContributor = mockk<CatalogContributor>(relaxed = true)
    private val locations = FakeStorageLocationRepository()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // A file per test: DataStore frees an open file only once the owning scope has
        // finished cancelling, so a fixed name makes the next test fail for this one.
        prefsFile = File(context.cacheDir, "settings-test-${System.nanoTime()}.preferences_pb")
        preferences = UserPreferencesRepository(
            dataStore = PreferenceDataStoreFactory.create(scope = prefsScope) { prefsFile },
            secretCipher = SecretCipher(),
        )
    }

    @After
    fun tearDown() {
        prefsFile.delete()
        prefsScope.cancel()
    }

    private fun viewModel() = SettingsViewModel(
        context = context,
        preferences = preferences,
        storageLocations = locations,
        backupManager = backupManager,
        autoBackupCatalog = autoBackupCatalog,
        diagnostics = DiagnosticsLog(context, FakeAppClock()),
        syncManager = syncManager,
        syncScheduler = syncScheduler,
        catalogContributor = catalogContributor,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private fun stored() = runBlocking { preferences.preferences.first() }

    @Test
    fun `settings are written where the rest of the app reads them`() = runTest {
        val vm = viewModel()

        vm.setNotificationsEnabled(false)
        vm.setSoonDays(5)
        vm.setQuietHours(enabled = true, startHour = 23, endHour = 7)
        advanceUntilIdle()

        val prefs = stored()
        assertEquals(false, prefs.notificationsEnabled)
        assertEquals(5, prefs.soonThresholdDays)
        assertEquals(true, prefs.quietHoursEnabled)
        assertEquals(23, prefs.quietStartHour)
    }

    /**
     * The import replaces everything. The copy taken first is the only way back for a user
     * who picked the wrong file, and it has to be written *before* the replace, not after.
     */
    @Test
    fun `a safety copy is taken before an import replaces the data`() = runTest {
        val file = File(context.cacheDir, "backup.json").apply { writeText("{}") }
        coEvery { backupManager.export() } returns """{"schemaVersion":2}"""
        val vm = viewModel()

        vm.importFrom(Uri.fromFile(file), BackupManager.ImportMode.REPLACE)
        advanceUntilIdle()

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            backupManager.export()
            backupManager.import(any(), BackupManager.ImportMode.REPLACE)
        }
        assertTrue(
            "the copy has to be on disk, not in memory",
            File(File(context.filesDir, "safety"), "before-import.json").exists(),
        )
    }

    @Test
    fun `a failed import says so instead of claiming success`() = runTest {
        val file = File(context.cacheDir, "broken.json").apply { writeText("not json") }
        coEvery { backupManager.import(any(), any()) } throws IllegalArgumentException("Not a valid backup file")
        val vm = viewModel()

        vm.importFrom(Uri.fromFile(file), BackupManager.ImportMode.REPLACE)
        advanceUntilIdle()

        assertEquals(R.string.backup_import_error, vm.message.value)
    }

    @Test
    fun `the chosen mode reaches the import`() = runTest {
        val file = File(context.cacheDir, "merge.json").apply { writeText("{}") }
        val vm = viewModel()

        vm.importFrom(Uri.fromFile(file), BackupManager.ImportMode.MERGE)
        advanceUntilIdle()

        coVerify { backupManager.import(any(), BackupManager.ImportMode.MERGE) }
    }

    @Test
    fun `turning on automatic backup remembers the folder`() = runTest {
        val vm = viewModel()

        vm.enableAutoBackup(Uri.parse("content://tree/backups"))
        advanceUntilIdle()

        val prefs = stored()
        assertEquals(true, prefs.autoBackupEnabled)
        assertEquals("content://tree/backups", prefs.autoBackupFolderUri)
        assertEquals(R.string.settings_auto_backup_on, vm.message.value)
    }

    /**
     * Turning it off clears the folder too. Keeping it would leave a background worker
     * pointed at a directory the user has stopped consenting to.
     */
    @Test
    fun `turning automatic backup off lets go of the folder`() = runTest {
        val vm = viewModel()
        vm.enableAutoBackup(Uri.parse("content://tree/backups"))
        advanceUntilIdle()

        vm.disableAutoBackup()
        advanceUntilIdle()

        assertEquals(false, stored().autoBackupEnabled)
    }

    @Test
    fun `the list of copies is only fetched when asked for`() = runTest {
        val entries = listOf(AutoBackupEntry("eatbefore-2026-08-14.json", Uri.parse("content://f/1"), 42L))
        coEvery { autoBackupCatalog.list() } returns entries
        val vm = viewModel()

        assertNull("nothing is listed until the row is tapped", vm.autoBackups.value)

        vm.loadAutoBackups()
        advanceUntilIdle()
        assertEquals(entries, vm.autoBackups.value)

        vm.clearAutoBackups()
        assertNull(vm.autoBackups.value)
    }

    @Test
    fun `sharing is turned on, scheduled and exchanged at once`() = runTest {
        coEvery { syncManager.sync() } returns SyncResult.Success(SyncStats(peersSeen = 1))
        val vm = viewModel()

        vm.enableSharing(Uri.parse("content://tree/shared"))
        advanceUntilIdle()

        assertEquals("content://tree/shared", stored().syncFolderUri)
        coVerify { syncScheduler.apply(any()) }
        coVerify { syncManager.sync() }
        assertEquals(R.string.settings_sharing_done, vm.message.value)
    }

    /** A folder with nobody else in it is not a failure, and must not be reported as one. */
    @Test
    fun `an exchange that met no one says so plainly`() = runTest {
        coEvery { syncManager.sync() } returns SyncResult.Success(SyncStats(peersSeen = 0))
        val vm = viewModel()

        vm.syncNow()
        advanceUntilIdle()

        assertEquals(R.string.settings_sharing_no_peers, vm.message.value)
    }

    @Test
    fun `each way an exchange can fail has its own message`() = runTest {
        val cases = mapOf(
            SyncResult.NotConfigured to R.string.settings_sharing_not_configured,
            SyncResult.FolderUnavailable to R.string.settings_sharing_folder_gone,
            SyncResult.Failed("boom") to R.string.settings_sharing_failed,
        )

        cases.forEach { (result, expected) ->
            coEvery { syncManager.sync() } returns result
            val vm = viewModel()

            vm.syncNow()
            advanceUntilIdle()

            assertEquals(result.toString(), expected, vm.message.value)
        }
    }

    @Test
    fun `turning sharing off stops the schedule as well`() = runTest {
        val vm = viewModel()
        vm.enableSharing(Uri.parse("content://tree/shared"))
        advanceUntilIdle()

        vm.disableSharing()
        advanceUntilIdle()

        assertNull(stored().syncFolderUri)
        assertEquals(R.string.settings_sharing_off, vm.message.value)
    }

    @Test
    fun `naming this phone is stored for the next exchange`() = runTest {
        val vm = viewModel()

        vm.setDeviceName("Кухонный телефон")
        advanceUntilIdle()

        assertEquals("Кухонный телефон", stored().deviceName)
    }

    /**
     * The password is encrypted with a key that dies with the app installation. Saying
     * "linked" from the username alone is how an account that could no longer be read
     * looked perfectly healthy while quietly doing nothing.
     */
    @Test
    fun `the catalog account is only called usable when the password can be read`() = runTest {
        coEvery { catalogContributor.isConfigured() } returns false
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(false, vm.catalogAccountUsable.value)
    }

    @Test
    fun `checking the catalog reports what it actually answered`() = runTest {
        val cases = mapOf(
            ContributionResult.Success to R.string.settings_off_check_ok,
            ContributionResult.AuthFailed to R.string.settings_off_check_auth_failed,
            ContributionResult.NotConfigured to R.string.settings_off_check_not_configured,
            ContributionResult.Failed("network") to R.string.settings_off_check_failed,
        )

        cases.forEach { (result, expected) ->
            coEvery { catalogContributor.checkAccount() } returns result
            val vm = viewModel()

            vm.checkCatalogAccount()
            advanceUntilIdle()

            assertEquals(result.toString(), expected, vm.message.value)
        }
    }

    @Test
    fun `unlinking the account clears the name`() = runTest {
        val vm = viewModel()
        vm.setOffAccount("alex", "secret")
        advanceUntilIdle()
        assertEquals("alex", stored().offUsername)

        vm.setOffAccount("", null)
        advanceUntilIdle()

        assertNull(stored().offUsername)
        assertEquals(R.string.settings_off_removed, vm.message.value)
    }

    @Test
    fun `a message is shown once and then let go`() = runTest {
        val vm = viewModel()
        vm.setDeviceName("x")
        vm.disableSharing()
        advanceUntilIdle()

        vm.consumeMessage()

        assertNull(vm.message.value)
    }

    /** Nothing has gone wrong yet, so there is nothing to hand over. */
    @Test
    fun `the diagnostics report is absent until something fails`() = runTest {
        assertNull(viewModel().diagnosticsReport())
    }
}
