package com.eatbefore

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Enables Hilt DI and supplies a [HiltWorkerFactory] so that
 * WorkManager (used for expiry notifications in a later milestone) can construct
 * dependency-injected workers.
 */
@HiltAndroidApp
class EatBeforeApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
