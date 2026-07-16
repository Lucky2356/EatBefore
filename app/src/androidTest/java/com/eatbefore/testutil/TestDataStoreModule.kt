package com.eatbefore.testutil

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.eatbefore.di.DataStoreModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton

/**
 * Replaces the production DataStore for instrumented tests.
 *
 * Hilt builds a fresh SingletonComponent per test method, so the production provider —
 * which always points at `user_prefs` — would open a second DataStore on a file the
 * previous test still holds ("multiple DataStores active for the same file"). Each test
 * therefore gets its own file, which also keeps tests isolated from each other and from
 * whatever the app has stored on the device.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TestDataStoreModule {

    private val counter = AtomicInteger(0)

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        val name = "test_prefs_${counter.incrementAndGet()}.preferences_pb"
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.cacheDir.resolve(name).apply { delete() } },
        )
    }
}
