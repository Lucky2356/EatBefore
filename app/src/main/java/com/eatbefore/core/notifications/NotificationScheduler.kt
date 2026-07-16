package com.eatbefore.core.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.datastore.UserPreferences
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

        val delayMinutes = minutesUntilNext(prefs.notificationHour, prefs.notificationMinute)
        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            ExpiryCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
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
    }
}
