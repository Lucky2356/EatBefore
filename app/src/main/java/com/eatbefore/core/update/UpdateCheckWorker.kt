package com.eatbefore.core.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eatbefore.core.datastore.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks once a day for a newer release and writes down what it found.
 *
 * It only records. Nothing is downloaded without being asked, and nothing appears in the
 * notification shade: the app is not important enough to interrupt someone's day over a
 * version number. The result shows up as a mark in settings, where it is looked at when
 * the user is already there.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updateChecker: UpdateChecker,
    private val updatePreferences: UpdatePreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (val result = updateChecker.check()) {
        is UpdateCheckResult.Available -> {
            updatePreferences.record(result.update.version.toString())
            Result.success()
        }

        UpdateCheckResult.UpToDate, UpdateCheckResult.NoApk -> {
            // Clearing the remembered version matters: without it a mark would stay on the
            // settings row after the update had already been installed.
            updatePreferences.record(null)
            Result.success()
        }

        // Offline is the ordinary case for a phone; try again on the next window rather
        // than burning retries against a network that is simply not there.
        is UpdateCheckResult.Failed -> Result.success()
    }
}

/** Keeps the daily [UpdateCheckWorker] in step with the user's setting. */
@Singleton
class UpdateScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    fun apply(prefs: UserPreferences) {
        val workManager = WorkManager.getInstance(context)
        if (!prefs.updateCheckEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    // Unmetered, because the answer to this question is never worth a
                    // byte of someone's mobile data.
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "update_check"
        private const val REPEAT_INTERVAL_HOURS = 24L
    }
}
