package com.eatbefore.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.eatbefore.core.notifications.ExpiryCheckWorker
import com.eatbefore.domain.usecase.AddManualProductUseCase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import javax.inject.Inject

/**
 * Runs the real [ExpiryCheckWorker] end-to-end on device: seeds an item expiring today via
 * the actual repositories/use cases, then verifies the worker succeeds and posts a system
 * notification. Exercises worker → repository → plan → notifier → NotificationManager.
 */
@HiltAndroidTest
class ExpiryCheckWorkerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var addManualProduct: AddManualProductUseCase

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hiltRule.inject()
        // Ensure notifications can be posted on Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                "android.permission.POST_NOTIFICATIONS",
            )
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
    @Test
    fun postsNotificationForExpiringStock() = runBlocking {
        // Seed a product expiring today so the plan has content.
        addManualProduct(
            AddManualProductUseCase.Params(
                name = "TestItem",
                storageLocationId = 1,
                quantity = 1.0,
                expirationDate = LocalDate.now(),
            ),
        )

        val worker = TestListenableWorkerBuilder<ExpiryCheckWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)

        val manager = context.getSystemService(NotificationManager::class.java)
        // Give the system a moment to register the posted notification.
        Thread.sleep(500)
        assertTrue(
            "Expected an active notification after the worker ran",
            manager.activeNotifications.isNotEmpty(),
        )
    }
}
