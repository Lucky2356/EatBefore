package com.eatbefore.feature.analytics

import app.cash.turbine.test
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType
import com.eatbefore.domain.usecase.BuildAnalyticsUseCase
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.FakeHistoryRepository
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.FakeProductRepository
import com.eatbefore.testutil.FakeStorageLocationRepository
import com.eatbefore.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The reports screen. The counting itself is `BuildAnalyticsUseCase`'s and well covered;
 * what was not covered is this layer's two jobs — cutting the history at the chosen period,
 * and turning current stock into "what is where" — both of which quietly produce plausible
 * numbers when they are wrong.
 */
class AnalyticsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeAppClock(Instant.parse("2026-08-14T10:00:00Z"))
    private val inventory = FakeInventoryRepository()
    private val history = FakeHistoryRepository(inventory)
    private val products = FakeProductRepository(mutableMapOf(1L to Product(id = 1, name = "Молоко")))
    private val fridge = StorageLocation(id = 1, name = "Холодильник", type = StorageType.FRIDGE)
    private val freezer = StorageLocation(id = 2, name = "Морозильник", type = StorageType.FREEZER)
    private val locations = FakeStorageLocationRepository(listOf(fridge, freezer))

    private fun record(type: EventType, daysAgo: Long) {
        inventory.events += InventoryEvent(
            id = inventory.events.size + 1L,
            inventoryBatchId = 1,
            productId = 1,
            eventType = type,
            createdAt = clock.now().minus(daysAgo, ChronoUnit.DAYS),
        )
    }

    private fun stock(location: StorageLocation, count: Int) = List(count) { index ->
        InventoryItem(
            batch = InventoryBatch(
                id = location.id * 100 + index,
                productId = 1,
                storageLocationId = location.id,
                quantity = 1.0,
                initialQuantity = 1.0,
            ),
            product = Product(id = 1, name = "Молоко"),
            location = location,
        )
    }

    private fun viewModel() = AnalyticsViewModel(
        historyRepository = history,
        productRepository = products,
        inventoryRepository = inventory,
        storageLocationRepository = locations,
        buildAnalytics = BuildAnalyticsUseCase(),
        clock = clock,
    )

    @Test
    fun `the default period is the last month`() = runTest {
        record(EventType.ADDED, daysAgo = 3)
        record(EventType.ADDED, daysAgo = 200)

        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertEquals(AnalyticsPeriod.MONTH, state.period)
            assertEquals(1, state.summary?.addedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Switching the period must re-cut the same history, not filter what was already cut. */
    @Test
    fun `a wider period brings older events back`() = runTest {
        record(EventType.ADDED, daysAgo = 3)
        record(EventType.ADDED, daysAgo = 200)
        val vm = viewModel()

        vm.uiState.test {
            awaitItemWhere { it.summary?.addedCount == 1 }

            vm.setPeriod(AnalyticsPeriod.YEAR)

            val year = awaitItemWhere { it.period == AnalyticsPeriod.YEAR }
            assertEquals(2, year.summary?.addedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a narrower period leaves out what happened before it`() = runTest {
        record(EventType.ADDED, daysAgo = 10)
        val vm = viewModel()

        vm.uiState.test {
            // Ten days ago is inside a month…
            awaitItemWhere { it.summary?.addedCount == 1 }

            vm.setPeriod(AnalyticsPeriod.WEEK)

            // …and outside a week.
            val week = awaitItemWhere { it.period == AnalyticsPeriod.WEEK }
            assertEquals(0, week.summary?.addedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stock is counted per place, busiest first`() = runTest {
        inventory.presentItems.value = stock(freezer, 1) + stock(fridge, 3)

        viewModel().uiState.test {
            val state = awaitItemWhere { it.byLocation.isNotEmpty() }
            assertEquals(listOf(fridge.id to 3, freezer.id to 1), state.byLocation.map { it.first.id to it.second })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** An empty place is not news; listing "Морозильник · 0" is filler. */
    @Test
    fun `places holding nothing are left out`() = runTest {
        inventory.presentItems.value = stock(fridge, 2)

        viewModel().uiState.test {
            val state = awaitItemWhere { it.byLocation.isNotEmpty() }
            assertEquals(listOf(fridge.id), state.byLocation.map { it.first.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty history has nothing to report`() = runTest {
        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertEquals(false, state.summary?.hasData)
            assertNull(state.summary?.usedInTimePercent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<AnalyticsUiState>.awaitItemWhere(
        predicate: (AnalyticsUiState) -> Boolean,
    ): AnalyticsUiState {
        repeat(MAX_EMISSIONS) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching state within $MAX_EMISSIONS emissions")
    }

    private companion object {
        const val MAX_EMISSIONS = 20
    }
}
