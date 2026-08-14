package com.eatbefore.feature.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.core.security.SecretCipher
import com.eatbefore.testutil.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * One method, and the smallest test in the app — kept because of what it guards rather
 * than what it covers. The root screen routes on this flag, so writing it to the wrong
 * place would leave the user on the introduction for good, with the intro's own "Начать"
 * button as the only thing on screen that appears to do nothing.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val prefsScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    private val prefsFile = File(
        ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
        "onboarding-test-${System.nanoTime()}.preferences_pb",
    )

    @After
    fun tearDown() {
        prefsFile.delete()
        prefsScope.cancel()
    }

    @Test
    fun `finishing the introduction is remembered`() = runTest {
        val preferences = UserPreferencesRepository(
            dataStore = PreferenceDataStoreFactory.create(scope = prefsScope) { prefsFile },
            secretCipher = SecretCipher(),
        )

        OnboardingViewModel(preferences).complete()
        advanceUntilIdle()

        assertTrue(preferences.preferences.first().onboardingCompleted)
    }
}
