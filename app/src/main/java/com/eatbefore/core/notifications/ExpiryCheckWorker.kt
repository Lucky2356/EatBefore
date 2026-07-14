package com.eatbefore.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.notification.isWithinQuietHours
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.usecase.BuildExpiryNotificationUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalTime

/**
 * Daily background job that checks expiring stock and posts a single batched notification.
 * Respects the user's on/off setting and quiet hours. Scheduled by [NotificationScheduler]
 * via WorkManager; the [androidx.hilt.work.HiltWorkerFactory] injects the dependencies.
 */
@HiltWorker
class ExpiryCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val inventoryRepository: InventoryRepository,
    private val preferences: UserPreferencesRepository,
    private val buildNotification: BuildExpiryNotificationUseCase,
    private val notifier: ExpiryNotifier,
    private val clock: AppClock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = preferences.preferences.first()
        if (!prefs.notificationsEnabled) return Result.success()

        val nowHour = LocalTime.ofInstant(clock.now(), clock.zone()).hour
        if (prefs.quietHoursEnabled &&
            isWithinQuietHours(nowHour, prefs.quietStartHour, prefs.quietEndHour)
        ) {
            return Result.success()
        }

        val today = clock.today()
        val threshold = today.plusDays(prefs.soonThresholdDays.toLong()).toEpochDay()
        val items = inventoryRepository.observeExpiringBefore(threshold).first()
        val plan = buildNotification(items, today, prefs.soonThresholdDays)
        notifier.notify(plan)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "expiry_check_daily"
    }
}
