package com.eatbefore

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.eatbefore.core.backup.AutoBackupScheduler
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.core.diagnostics.CrashReporter
import com.eatbefore.core.notifications.ExpiryNotifier
import com.eatbefore.core.notifications.NotificationScheduler
import com.eatbefore.core.sync.SyncScheduler
import com.eatbefore.core.update.UpdateScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Enables Hilt DI and supplies a [HiltWorkerFactory] so that
 * WorkManager (expiry notifications) can construct dependency-injected workers. On start
 * it creates the notification channel and keeps the daily reminder schedule in sync with
 * user settings.
 */
@HiltAndroidApp
class EatBeforeApplication :
    Application(),
    Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var notificationScheduler: NotificationScheduler

    @Inject lateinit var autoBackupScheduler: AutoBackupScheduler

    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var updateScheduler: UpdateScheduler

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject lateinit var expiryNotifier: ExpiryNotifier

    @Inject lateinit var crashReporter: CrashReporter

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // First, so that a failure in anything below is still recorded.
        crashReporter.install()
        expiryNotifier.ensureChannel()
        // Re-apply the schedule whenever notification-related settings change.
        appScope.launch {
            userPreferencesRepository.preferences
                .distinctUntilChanged { old, new ->
                    old.notificationsEnabled == new.notificationsEnabled &&
                        old.notificationHour == new.notificationHour &&
                        old.notificationMinute == new.notificationMinute
                }
                .onEach { notificationScheduler.apply(it) }
                .collect {}
        }

        appScope.launch {
            userPreferencesRepository.preferences
                .distinctUntilChanged { old, new ->
                    old.autoBackupEnabled == new.autoBackupEnabled &&
                        old.autoBackupFolderUri == new.autoBackupFolderUri
                }
                .onEach { autoBackupScheduler.apply(it) }
                .collect {}
        }

        appScope.launch {
            userPreferencesRepository.preferences
                .distinctUntilChanged { old, new -> old.syncFolderUri == new.syncFolderUri }
                .onEach { syncScheduler.apply(it) }
                .collect {}
        }

        appScope.launch {
            userPreferencesRepository.preferences
                .distinctUntilChanged { old, new -> old.updateCheckEnabled == new.updateCheckEnabled }
                .onEach { updateScheduler.apply(it) }
                .collect {}
        }
    }
}
