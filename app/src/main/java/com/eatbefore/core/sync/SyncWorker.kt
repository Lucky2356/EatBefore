package com.eatbefore.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic household exchange. Scheduled by [SyncScheduler].
 *
 * A missing folder is *not* retried: the user removed it or revoked the permission, and
 * only they can fix that. Genuine failures (a half-written cloud file, no storage) do
 * retry, since they usually resolve themselves.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (syncManager.sync()) {
        is SyncResult.Success -> Result.success()
        SyncResult.NotConfigured, SyncResult.FolderUnavailable -> Result.success()
        is SyncResult.Failed -> Result.retry()
    }
}
