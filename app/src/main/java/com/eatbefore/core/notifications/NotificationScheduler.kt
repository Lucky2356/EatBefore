package com.eatbefore.core.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.datastore.UserPreferences
import com.eatbefore.domain.notification.reminderTime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules (or cancels) the daily [ExpiryCheckWorker] based on user settings. A single
 * unique periodic work runs every 24h, first firing at the user's chosen time. Uses UPDATE
 * policy so re-scheduling on settings change adjusts the existing job rather than stacking.
 */
@Singleton
class NotificationScheduler @Inject constructor(@ApplicationContext private val context: Context, private val clock: AppClock) {

    fun apply(prefs: UserPreferences) {
        val workManager = WorkManager.getInstance(context)
        if (!prefs.notificationsEnabled) {
            workManager.cancelUniqueWork(ExpiryCheckWorker.WORK_NAME)
            return
        }

        val (hour, minute) = reminderTime(
            prefs.notificationHour,
            prefs.notificationMinute,
            prefs.quietHoursEnabled,
            prefs.quietStartHour,
            prefs.quietEndHour,
        )
        val delayMinutes = minutesUntilNext(hour, minute)
        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            ExpiryCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * The day's check landed inside quiet hours anyway — WorkManager runs periodic work
     * late when the phone dozes. Rather than drop that day's reminder, check again once the
     * quiet hours are over.
     */
    fun deferUntilQuietHoursEnd(prefs: UserPreferences) {
        val request = OneTimeWorkRequestBuilder<ExpiryCheckWorker>()
            .setInitialDelay(minutesUntilNext(prefs.quietEndHour, 0), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DEFERRED_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Minutes from now until the next occurrence of [hour]:[minute] in the local zone. */
    private fun minutesUntilNext(hour: Int, minute: Int): Long {
        val now = LocalDateTime.ofInstant(clock.now(), clock.zone())
        val todayTarget = LocalDateTime.of(LocalDate.from(now), LocalTime.of(hour, minute))
        val target = if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
        return Duration.between(now, target).toMinutes().coerceAtLeast(0)
    }

    private companion object {
        /** The reminder is a once-a-day digest. */
        const val REPEAT_INTERVAL_HOURS = 24L
        const val DEFERRED_WORK_NAME = "expiry_check_after_quiet_hours"
    }
}
