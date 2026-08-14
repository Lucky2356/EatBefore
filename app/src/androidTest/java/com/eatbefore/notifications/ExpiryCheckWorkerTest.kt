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
import com.eatbefore.R
import com.eatbefore.core.database.EatBeforeDatabase
import com.eatbefore.core.notifications.ExpiryCheckWorker
import com.eatbefore.core.notifications.ExpiryNotifier
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

    @Inject lateinit var db: EatBeforeDatabase

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        hiltRule.inject()
        // Instrumented tests share one database on the device, so stock left by an
        // earlier test would change what this reminder is about. Locations stay: they
        // are seeded on create and the batches below refer to them.
        db.inventoryEventDao().deleteAll()
        db.inventoryBatchDao().deleteAll()
        context.getSystemService(NotificationManager::class.java).cancelAll()
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

    /**
     * With one product going off, the two answers it can have are buttons in the shade:
     * the errand is usually already done in the kitchen, and making the user unlock the
     * phone to record it is how a reminder turns into a chore. Checked on device because
     * the actions only exist once the notification has actually been built and posted.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
    @Test
    fun singleProductGetsWriteOffActionsInTheShade() = runBlocking {
        addManualProduct(
            AddManualProductUseCase.Params(
                name = "SoloItem",
                storageLocationId = 1,
                quantity = 1.0,
                expirationDate = LocalDate.now(),
            ),
        )

        val worker = TestListenableWorkerBuilder<ExpiryCheckWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        worker.doWork()
        Thread.sleep(500)

        val manager = context.getSystemService(NotificationManager::class.java)
        val posted = manager.activeNotifications
            .first { it.id == ExpiryNotifier.NOTIFICATION_ID }
            .notification
        val labels = posted.actions.orEmpty().map { it.title.toString() }

        assertTrue(
            "Expected the two write-off actions, got $labels",
            labels.containsAll(
                listOf(
                    context.getString(R.string.notif_action_consumed),
                    context.getString(R.string.notif_action_discarded),
                ),
            ),
        )
    }
}
