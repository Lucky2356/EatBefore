package com.eatbefore.feature.home

import com.eatbefore.core.datastore.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DataSafetyWarningTest {

    private val now = Instant.parse("2026-09-20T10:00:00Z")

    private fun ago(duration: Duration) = now.minus(duration).toEpochMilli()

    private val backupOn = UserPreferences(autoBackupEnabled = true, autoBackupFolderUri = "content://tree/backups")
    private val sharingOn = UserPreferences(syncFolderUri = "content://tree/shared")

    @Test
    fun `nothing is said while both jobs are switched off`() {
        assertNull(dataSafetyWarning(UserPreferences(), now))
    }

    @Test
    fun `a backup written yesterday is fine`() {
        val prefs = backupOn.copy(lastAutoBackupAt = ago(Duration.ofDays(1)))
        assertNull(dataSafetyWarning(prefs, now))
    }

    @Test
    fun `a backup that stopped more than three days ago is reported`() {
        val prefs = backupOn.copy(lastAutoBackupAt = ago(Duration.ofDays(3).plusHours(1)))
        assertEquals(DataSafetyWarning.BACKUP_STALLED, dataSafetyWarning(prefs, now))
    }

    /** Turning it on must not warn before the first run has had a chance to happen. */
    @Test
    fun `a backup switched on an hour ago is not yet overdue`() {
        val prefs = backupOn.copy(autoBackupArmedAt = ago(Duration.ofHours(1)))
        assertNull(dataSafetyWarning(prefs, now))
    }

    /**
     * On since before the switch-on moment was recorded, and never a single copy: the
     * folder has been broken all along, which is the case this exists for.
     */
    @Test
    fun `a backup that has never once worked is reported`() {
        assertEquals(DataSafetyWarning.BACKUP_STALLED, dataSafetyWarning(backupOn, now))
    }

    @Test
    fun `a backup switched off is never reported, however old`() {
        val prefs = backupOn.copy(autoBackupEnabled = false)
        assertNull(dataSafetyWarning(prefs, now))
    }

    @Test
    fun `an exchange that stopped is reported`() {
        val prefs = sharingOn.copy(lastSyncAt = ago(Duration.ofDays(4)))
        assertEquals(DataSafetyWarning.SYNC_STALLED, dataSafetyWarning(prefs, now))
    }

    @Test
    fun `a recent exchange is fine`() {
        val prefs = sharingOn.copy(lastSyncAt = ago(Duration.ofHours(6)))
        assertNull(dataSafetyWarning(prefs, now))
    }

    /** Losing every copy is worse than two phones disagreeing, so that is what is shown. */
    @Test
    fun `a stalled backup is shown ahead of a stalled exchange`() {
        val prefs = UserPreferences(
            autoBackupEnabled = true,
            autoBackupFolderUri = "content://tree/backups",
            syncFolderUri = "content://tree/shared",
        )
        assertEquals(DataSafetyWarning.BACKUP_STALLED, dataSafetyWarning(prefs, now))
    }
}
