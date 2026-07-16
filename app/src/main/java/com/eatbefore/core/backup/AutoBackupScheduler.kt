package com.eatbefore.core.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.eatbefore.core.datastore.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the daily [AutoBackupWorker] in sync with the user's setting. Mirrors
 * [com.eatbefore.core.notifications.NotificationScheduler]: one unique periodic job,
 * UPDATE policy so re-enabling adjusts rather than stacks.
 */
@Singleton
class AutoBackupScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    fun apply(prefs: UserPreferences) {
        val workManager = WorkManager.getInstance(context)
        if (!prefs.autoBackupEnabled || prefs.autoBackupFolderUri == null) {
            workManager.cancelUniqueWork(AutoBackupWorker.WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            // Writing to a cloud-backed folder can need the network; a backup is never
            // urgent enough to justify draining a low battery.
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            AutoBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private companion object {
        const val REPEAT_INTERVAL_HOURS = 24L
    }
}
