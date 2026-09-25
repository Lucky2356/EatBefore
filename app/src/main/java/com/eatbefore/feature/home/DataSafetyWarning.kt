package com.eatbefore.feature.home

import com.eatbefore.core.datastore.UserPreferences
import java.time.Duration
import java.time.Instant

/**
 * Something that is meant to be keeping the data safe has quietly stopped.
 *
 * Both jobs run in the background with nobody watching: a revoked folder, a phone that
 * kills background work, or a cloud client that stopped syncing all look exactly like
 * "nothing to do" from the outside. The date of the last success sits deep in settings,
 * where nobody goes to check — so the home screen says it instead.
 */
enum class DataSafetyWarning {
    /** Automatic backup is on, but no copy has been written for a while. */
    BACKUP_STALLED,

    /** A shared folder is chosen, but no exchange has finished for a while. */
    SYNC_STALLED,
}

/**
 * How long either job may go without a success before the home screen speaks up. Backup
 * runs daily and the exchange every six hours, so three days is several missed runs in a
 * row — not one unlucky night with the battery low.
 */
internal val DATA_SAFETY_GRACE: Duration = Duration.ofDays(3)

/**
 * The one warning worth showing, or null. Backup comes first: a stalled exchange leaves two
 * phones that disagree, a stalled backup leaves nothing to come back to when one is lost.
 *
 * Measured from the later of the last success and the moment the job was switched on, so
 * that turning it on does not warn at once, and a job that has never succeeded still
 * warns once its grace runs out. Both zero means it was switched on before that moment was
 * recorded and has never worked since — which is exactly the case to report.
 */
internal fun dataSafetyWarning(prefs: UserPreferences, now: Instant): DataSafetyWarning? {
    fun stalled(lastSuccess: Long, armedAt: Long): Boolean {
        val since = Instant.ofEpochMilli(maxOf(lastSuccess, armedAt))
        return Duration.between(since, now) > DATA_SAFETY_GRACE
    }
    return when {
        prefs.autoBackupEnabled &&
            prefs.autoBackupFolderUri != null &&
            stalled(prefs.lastAutoBackupAt, prefs.autoBackupArmedAt) -> DataSafetyWarning.BACKUP_STALLED
        prefs.syncFolderUri != null &&
            stalled(prefs.lastSyncAt, prefs.syncArmedAt) -> DataSafetyWarning.SYNC_STALLED
        else -> null
    }
}
