package com.eatbefore.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.repository.HistoryRepository
import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.repository.ProductRepository
import com.eatbefore.domain.usecase.BuildWeeklySummaryUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** Sunday evening: posts the week's summary, if there is anything to say. */
@HiltWorker
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferences: UserPreferencesRepository,
    private val historyRepository: HistoryRepository,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val buildSummary: BuildWeeklySummaryUseCase,
    private val notifier: WeeklySummaryNotifier,
    private val clock: AppClock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = preferences.preferences.first()
        if (!prefs.notificationsEnabled || !prefs.weeklySummaryEnabled) return Result.success()

        val summary = buildSummary(
            events = historyRepository.observeAll().first(),
            productsById = productRepository.observeAll().first().associateBy { it.id },
            prices = inventoryRepository.observePrices().first(),
            present = inventoryRepository.observePresentByExpiry().first(),
            now = clock.now(),
            zone = clock.zone(),
        )
        summary?.let(notifier::notify)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "weekly_summary"
    }
}
