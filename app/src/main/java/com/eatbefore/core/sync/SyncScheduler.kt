package com.eatbefore.core.sync

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
 * Keeps the periodic [SyncWorker] in step with the user's setting.
 *
 * Six hours, not fifteen minutes: the shared folder lives on a cloud drive that syncs on
 * its own schedule anyway, so polling harder would burn battery without making changes
 * arrive sooner. The app also syncs when opened, which is what actually makes it feel
 * current.
 */
@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    fun apply(prefs: UserPreferences) {
        val workManager = WorkManager.getInstance(context)
        if (prefs.syncFolderUri == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<SyncWorker>(REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "household_sync"
        private const val REPEAT_INTERVAL_HOURS = 6L
    }
}
