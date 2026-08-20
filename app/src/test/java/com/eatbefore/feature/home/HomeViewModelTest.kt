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
import org.junit.Assert.assertNull
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

    private fun opened(item: InventoryItem) =
        item.copy(batch = item.batch.copy(openedAt = clock.now()))

    private fun item(id: Long, expiry: LocalDate?) = InventoryItem(
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
     * The count and the axis have to agree. The list used to stop at ten while the count
     * kept going, which understated nothing but did leave five batches off a screen that
     * claimed to show what was going off.
     */
    @Test
    fun `the expired count and the axis agree`() = runTest {
        inventory.expiringItems.value = (1L..15L).map { item(it, today.minusDays(1)) }

        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading && it.eatFirst != null }
            assertEquals(15, state.expiredCount)
            // All but the one the card is already showing.
            assertEquals(14, state.timeline.sumOf { it.rows.size })
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

    /** The headline number: what has gone off, plus what runs out before the day is over. */
    @Test
    fun `the summary counts what is off and what runs out today`() = runTest {
        inventory.expiringItems.value = listOf(
            item(1, today.minusDays(2)),
            item(2, today),
            item(3, today.plusDays(2)),
        )

        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertEquals(2, state.needsAttentionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Zero means the home screen shows no banner at all — see HomeScreen. */
    @Test
    fun `nothing urgent means nothing to lead with`() = runTest {
        inventory.expiringItems.value = listOf(item(1, today.plusDays(2)))

        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertEquals(0, state.needsAttentionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * An opened pack beats a sealed one that expires sooner: opening it started a clock
     * the printed date knows nothing about, and half a carton left open is what actually
     * gets thrown away.
     */
    @Test
    fun `the card picks an opened pack over a sealed one expiring sooner`() = runTest {
        inventory.expiringItems.value = listOf(
            item(1, today.plusDays(1)),
            opened(item(2, today.plusDays(3))),
        )

        viewModel().uiState.test {
            val state = awaitItemWhere { it.eatFirst != null }
            assertEquals(2L, state.eatFirst?.batchId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `among equals the nearest date wins`() = runTest {
        inventory.expiringItems.value = listOf(
            item(1, today.plusDays(3)),
            item(2, today),
            item(3, today.plusDays(1)),
        )

        viewModel().uiState.test {
            val state = awaitItemWhere { it.eatFirst != null }
            assertEquals(2L, state.eatFirst?.batchId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The same row as a card and as a list item reads as two different products. */
    @Test
    fun `what the card shows is not repeated in the list below it`() = runTest {
        inventory.expiringItems.value = listOf(item(1, today), item(2, today.plusDays(2)))

        viewModel().uiState.test {
            val state = awaitItemWhere { it.eatFirst != null }
            assertEquals(1L, state.eatFirst?.batchId)
            assertEquals(listOf(2L), state.timeline.flatMap { it.rows }.map { it.batchId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Nothing to say beats "hurry up" with no date to hurry towards. */
    @Test
    fun `a batch without a date is never the one to eat first`() = runTest {
        inventory.expiringItems.value = listOf(item(1, expiry = null))

        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertNull(state.eatFirst)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The filter must show exactly what the banner counted. Landing on a list holding
     * more or fewer items than the number just tapped makes the number untrustworthy.
     */
    @Test
    fun `tapping the summary asks the stock list for the same set`() = runTest {
        viewModel().requestAttentionFilter()

        assertEquals(InventoryStatusFilter.TODAY, filterRequest.pending.value)
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
