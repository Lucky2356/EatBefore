package com.eatbefore.feature.history

import app.cash.turbine.test
import com.eatbefore.core.datastore.UserPreferences
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.usecase.RestoreBatchUseCase
import com.eatbefore.domain.usecase.UndoLastActionUseCase
import com.eatbefore.testutil.FakeHistoryRepository
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * The history is the app's undo mechanism: it is where a mistaken write-off is taken back,
 * and it is the only place that can say the other person did something. Both were checked
 * only by scrolling the screen by hand.
 */
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inventory = FakeInventoryRepository()
    private val history = FakeHistoryRepository(inventory)
    private val restoreBatch = mockk<RestoreBatchUseCase>(relaxed = true)
    private val undoLastAction = mockk<UndoLastActionUseCase>(relaxed = true)

    private fun seed(count: Int, type: EventType = EventType.ADDED) {
        repeat(count) { index ->
            inventory.events += InventoryEvent(
                id = (index + 1).toLong(),
                inventoryBatchId = 1,
                productId = 1,
                eventType = type,
                createdAt = Instant.ofEpochSecond(index.toLong()),
            )
        }
    }

    private fun viewModel(preferences: UserPreferences = UserPreferences()): HistoryViewModel {
        val prefs = mockk<UserPreferencesRepository>()
        every { prefs.preferences } returns flowOf(preferences)
        return HistoryViewModel(history, restoreBatch, undoLastAction, prefs)
    }

    @Test
    fun `a short history fits on one page`() = runTest {
        seed(3)

        viewModel().uiState.test {
            val state = awaitItemWhere { it.events.isNotEmpty() }
            assertEquals(3, state.events.size)
            assertFalse("nothing more to fetch", state.canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** One row beyond the page is what tells the screen another page exists. */
    @Test
    fun `a long history reports that there is more`() = runTest {
        seed(PAGE_SIZE + 5)

        viewModel().uiState.test {
            val state = awaitItemWhere { it.events.isNotEmpty() }
            assertEquals(PAGE_SIZE, state.events.size)
            assertTrue(state.canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reaching the end grows the page`() = runTest {
        seed(PAGE_SIZE + 5)
        val vm = viewModel()

        vm.uiState.test {
            awaitItemWhere { it.events.size == PAGE_SIZE }

            vm.loadMore()

            val grown = awaitItemWhere { it.events.size > PAGE_SIZE }
            assertEquals(PAGE_SIZE + 5, grown.events.size)
            assertFalse(grown.canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Choosing a filter starts a new list. Keeping the grown page would show a hundred rows
     * of "added" and then stop, with no way to tell whether that was all of them.
     */
    @Test
    fun `choosing a filter starts the paging over`() = runTest {
        seed(PAGE_SIZE + 5)
        val vm = viewModel()

        vm.uiState.test {
            awaitItemWhere { it.events.size == PAGE_SIZE }
            vm.loadMore()
            awaitItemWhere { it.events.size > PAGE_SIZE }

            vm.setFilter(EventType.ADDED)

            val filtered = awaitItemWhere { it.filter == EventType.ADDED }
            assertEquals(PAGE_SIZE, filtered.events.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `asking for more when there is none changes nothing`() = runTest {
        seed(3)
        val vm = viewModel()

        vm.uiState.test {
            awaitItemWhere { it.events.size == 3 }

            vm.loadMore()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Only what removed stock can be put back. Offering "restore" against an ADDED event
     * would be a button that quietly does nothing.
     */
    @Test
    fun `only write-offs can be restored`() = runTest {
        val vm = viewModel()

        listOf(EventType.CONSUMED, EventType.DISCARDED, EventType.EXPIRED).forEach {
            assertTrue(it.name, vm.isRestorable(event(it)))
        }
        listOf(EventType.ADDED, EventType.OPENED, EventType.MOVED, EventType.RESTORED).forEach {
            assertFalse(it.name, vm.isRestorable(event(it)))
        }
    }

    @Test
    fun `restoring goes through the use case`() = runTest {
        val vm = viewModel()

        vm.restore(event(EventType.DISCARDED))
        advanceUntilIdle()

        coVerify { restoreBatch(7L) }
    }

    @Test
    fun `undo last action goes through the use case`() = runTest {
        val vm = viewModel()

        vm.undoLast()
        advanceUntilIdle()

        coVerify { undoLastAction() }
    }

    /** The names arrive with the peer's journal; without them a row can only say "id". */
    @Test
    fun `the names of the other devices reach the screen`() = runTest {
        seed(1)

        viewModel(UserPreferences(peerNames = mapOf("device-b" to "Телефон Алексея"))).uiState.test {
            val state = awaitItemWhere { it.peerNames.isNotEmpty() }
            assertEquals("Телефон Алексея", state.peerNames["device-b"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun event(type: EventType) = InventoryEvent(
        id = 1,
        inventoryBatchId = 7,
        productId = 1,
        eventType = type,
    )

    private suspend fun app.cash.turbine.ReceiveTurbine<HistoryUiState>.awaitItemWhere(
        predicate: (HistoryUiState) -> Boolean,
    ): HistoryUiState {
        repeat(MAX_EMISSIONS) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching state within $MAX_EMISSIONS emissions")
    }

    private companion object {
        /** Mirrors HistoryViewModel.PAGE_SIZE, which is private to it. */
        const val PAGE_SIZE = 100
        const val MAX_EMISSIONS = 20
    }
}
