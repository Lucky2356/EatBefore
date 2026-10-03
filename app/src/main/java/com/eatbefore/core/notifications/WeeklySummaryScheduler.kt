package com.eatbefore.core.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.datastore.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules [WeeklySummaryWorker] for Sunday evenings, or cancels it. */
@Singleton
class WeeklySummaryScheduler @Inject constructor(@ApplicationContext private val context: Context, private val clock: AppClock) {

    fun apply(prefs: UserPreferences) {
        val workManager = WorkManager.getInstance(context)
        if (!prefs.notificationsEnabled || !prefs.weeklySummaryEnabled) {
            workManager.cancelUniqueWork(WeeklySummaryWorker.WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(DAYS_IN_WEEK, TimeUnit.DAYS)
            .setInitialDelay(minutesUntilSundayEvening(), TimeUnit.MINUTES)
            .build()
        // KEEP: re-applying on every start must not push next Sunday's run further away.
        workManager.enqueueUniquePeriodicWork(WeeklySummaryWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun minutesUntilSundayEvening(): Long {
        val now = LocalDateTime.ofInstant(clock.now(), clock.zone())
        var target = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(SUMMARY_TIME)
        if (!target.isAfter(now)) target = target.plusWeeks(1)
        return Duration.between(now, target).toMinutes()
    }

    private companion object {
        const val DAYS_IN_WEEK = 7L
        val SUMMARY_TIME: LocalTime = LocalTime.of(19, 0)
    }
}
