package com.eatbefore.feature.home

import app.cash.turbine.test
import com.eatbefore.core.datastore.UserPreferences
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import com.eatbefore.feature.common.QuickActions
import com.eatbefore.feature.inventory.InventoryFilterRequest
import com.eatbefore.feature.inventory.InventoryStatusFilter
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeAppClock()
    private val today: LocalDate get() = clock.today()
    private val fridge = StorageLocation(id = 1, name = "Fridge", type = StorageType.FRIDGE)
    private val inventory = FakeInventoryRepository()
    private val filterRequest = InventoryFilterRequest()

    private fun item(id: Long, expiry: LocalDate) = InventoryItem(
        batch = InventoryBatch(
            id = id,
            productId = id,
            storageLocationId = fridge.id,
            quantity = 1.0,
            initialQuantity = 1.0,
            expirationDate = expiry,
        ),
        product = Product(id = id, name = "Товар $id"),
        location = fridge,
    )

    private fun viewModel(): HomeViewModel {
        val prefs = mockk<UserPreferencesRepository>()
        every { prefs.preferences } returns flowOf(UserPreferences(soonThresholdDays = 3))
        return HomeViewModel(
            inventoryRepository = inventory,
            preferences = prefs,
            determineExpiryStatus = DetermineExpiryStatusUseCase(),
            quickActions = mockk<QuickActions>(relaxed = true),
            filterRequest = filterRequest,
            clock = clock,
        )
    }

    @Test
    fun `expired batches are counted apart from the ones still running out`() = runTest {
        inventory.expiringItems.value = listOf(
            item(1, today.minusDays(3)),
            item(2, today.minusDays(1)),
            item(3, today.plusDays(2)),
        )

        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertEquals(2, state.expiredCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The list shows at most ten; the count must not. Understating the problem exactly
     * when there is most of it would be the worst time to do it.
     */
    @Test
    fun `the expired count covers more than the visible list`() = runTest {
        inventory.expiringItems.value = (1L..15L).map { item(it, today.minusDays(1)) }

        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertEquals(15, state.expiredCount)
            assertEquals(10, state.expiringSoon.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing expired means nothing to answer for`() = runTest {
        inventory.expiringItems.value = listOf(item(1, today.plusDays(1)))

        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertEquals(0, state.expiredCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping the expired tile asks the stock list to open filtered`() = runTest {
        viewModel().requestExpiredFilter()

        assertEquals(InventoryStatusFilter.EXPIRED, filterRequest.pending.value)
    }
}

/** See the note in InventoryViewModelTest: state flows conflate, so filter rather than index. */
private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitItemWhere(predicate: (T) -> Boolean): T {
    repeat(MAX_EMISSIONS) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
    error("No matching item within $MAX_EMISSIONS emissions")
}

private const val MAX_EMISSIONS = 20
