package com.eatbefore.feature.product

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.eatbefore.R
import com.eatbefore.core.datastore.UserPreferences
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.ExpiryStatus
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.domain.usecase.AddToShoppingListUseCase
import com.eatbefore.domain.usecase.ChangeQuantityUseCase
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import com.eatbefore.domain.usecase.MarkBatchStatusUseCase
import com.eatbefore.domain.usecase.MoveBatchUseCase
import com.eatbefore.domain.usecase.OpenBatchUseCase
import com.eatbefore.domain.usecase.UndoLastActionUseCase
import com.eatbefore.domain.usecase.UpdateItemDetailsUseCase
import com.eatbefore.navigation.Routes
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.FakeHistoryRepository
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * The product screen is where the destructive actions live — used up, discarded, expired,
 * moved — and each of them has to leave the user a way back. Until now that was checked
 * only through the real UI, which is slow and says nothing about *why* something broke.
 */
class ProductViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val batchId = 7L
    private val clock = FakeAppClock(Instant.parse("2026-08-01T10:00:00Z"))
    private val inventory = FakeInventoryRepository()
    private val history = FakeHistoryRepository(inventory)

    private val fridge = StorageLocation(id = 1, name = "Холодильник")
    private val product = Product(id = 3, name = "Молоко")
    private val batch = InventoryBatch(
        id = batchId,
        productId = 3,
        storageLocationId = 1,
        quantity = 2.0,
        initialQuantity = 2.0,
        expirationDate = LocalDate.of(2026, 8, 3),
    )

    private val locations = object : StorageLocationRepository {
        override fun observeActive(): Flow<List<StorageLocation>> = flowOf(listOf(fridge))
        override fun observeAll(): Flow<List<StorageLocation>> = flowOf(listOf(fridge))
        override suspend fun getById(id: Long) = fridge
        override suspend fun getDefault() = fridge
        override suspend fun setDefault(id: Long) = Unit
        override suspend fun upsert(location: StorageLocation) = fridge.id
    }

    private val openBatch = mockk<OpenBatchUseCase>(relaxed = true)
    private val changeQuantity = mockk<ChangeQuantityUseCase>(relaxed = true)
    private val markStatus = mockk<MarkBatchStatusUseCase>(relaxed = true)
    private val moveBatch = mockk<MoveBatchUseCase>(relaxed = true)
    private val undoLastAction = mockk<UndoLastActionUseCase>(relaxed = true)
    private val updateItemDetails = mockk<UpdateItemDetailsUseCase>(relaxed = true)
    private val addToShoppingList = mockk<AddToShoppingListUseCase>(relaxed = true)
    private val addBatch = mockk<com.eatbefore.domain.usecase.AddBatchUseCase>(relaxed = true)

    private fun viewModel(preferences: UserPreferences = UserPreferences()): ProductViewModel {
        inventory.batches[batchId] = batch
        inventory.setObservedItem(batchId, InventoryItem(batch, product, fridge))
        val prefs = mockk<UserPreferencesRepository>()
        every { prefs.preferences } returns flowOf(preferences)
        return ProductViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Routes.PRODUCT_BATCH_ARG to batchId)),
            inventoryRepository = inventory,
            historyRepository = history,
            storageLocationRepository = locations,
            determineExpiryStatus = DetermineExpiryStatusUseCase(),
            preferences = prefs,
            openBatch = openBatch,
            changeQuantity = changeQuantity,
            markStatus = markStatus,
            moveBatch = moveBatch,
            undoLastAction = undoLastAction,
            updateItemDetails = updateItemDetails,
            addToShoppingList = addToShoppingList,
            addBatch = addBatch,
            clock = clock,
        )
    }

    @Test
    fun `loads the batch with its expiry status and days left`() = runTest {
        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertEquals(product, state.item?.product)
            assertEquals(ExpiryStatus.EXPIRING_SOON, state.expiryStatus)
            assertEquals(2L, state.remainingDays)
            assertEquals(listOf(fridge), state.locations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Every destructive action must arm the undo snackbar, or it cannot be taken back. */
    @Test
    fun `discarding offers undo and says what happened`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.discard()
            val state = awaitItemWhere { it.undoableActionAt != null }

            assertEquals(clock.now().toEpochMilli(), state.undoableActionAt)
            assertEquals(R.string.event_discarded, state.actionMessageRes)
            coVerify { markStatus(batchId, BatchStatus.DISCARDED) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `using it up offers to put the product on the shopping list`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.markFinished()

            assertTrue(awaitItemWhere { it.undoableActionAt != null }.offerShoppingList)
            coVerify { changeQuantity(batchId, 0.0) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Merely opening or moving something is not a reason to suggest buying more of it. */
    @Test
    fun `opening the pack does not offer the shopping list`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.open()

            val state = awaitItemWhere { it.undoableActionAt != null }
            assertFalse(state.offerShoppingList)
            assertEquals(R.string.event_opened, state.actionMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** In detailed mode the user types a remaining amount; zero means it ran out. */
    @Test
    fun `setting the remaining amount to zero counts as running out`() = runTest {
        val vm = viewModel(UserPreferences(detailedQuantityMode = true))
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.setQuantity(0.0)

            assertTrue(awaitItemWhere { it.undoableActionAt != null }.offerShoppingList)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setting a remaining amount above zero does not`() = runTest {
        val vm = viewModel(UserPreferences(detailedQuantityMode = true))
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.setQuantity(0.5)

            assertFalse(awaitItemWhere { it.undoableActionAt != null }.offerShoppingList)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `declining the shopping offer closes it without adding anything`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.markFinished()
            awaitItemWhere { it.offerShoppingList }

            vm.dismissShoppingOffer()
            awaitItemWhere { !it.offerShoppingList }
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { addToShoppingList(any()) }
    }

    @Test
    fun `accepting the shopping offer adds the product and closes the offer`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.markFinished()
            awaitItemWhere { it.offerShoppingList }

            vm.acceptShoppingOffer()
            awaitItemWhere { !it.offerShoppingList }
            cancelAndIgnoreRemainingEvents()
        }
        coVerify {
            addToShoppingList(
                AddToShoppingListUseCase.Params(productId = 3, sourceInventoryBatchId = batchId),
            )
        }
    }

    /** Undo has to clear the snackbar too, or it stays on screen offering to undo twice. */
    @Test
    fun `undo runs the action and disarms the snackbar`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.discard()
            awaitItemWhere { it.undoableActionAt != null }

            vm.undo()
            val state = awaitItemWhere { it.undoableActionAt == null }
            assertNull(state.actionMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { undoLastAction() }
    }

    /**
     * The screen must pop only when an action removed the batch. On first load the item is
     * legitimately absent for an instant, and closing then would bounce the user straight
     * back out of a screen they just opened.
     */
    @Test
    fun `a missing item does not close the screen before anything has happened`() = runTest {
        val vm = viewModel()
        inventory.setObservedItem(batchId, null)

        vm.uiState.test {
            assertFalse(awaitItemWhere { !it.isLoading }.closed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the screen closes once an action has removed the batch`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.markExpired()
            awaitItemWhere { it.undoableActionAt != null }

            inventory.setObservedItem(batchId, null)
            assertTrue(awaitItemWhere { it.closed }.closed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Quick "buy again" from the card: the item stays in stock, nothing is written off. */
    @Test
    fun `adding to the shopping list from the card leaves the batch alone`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.addToShopping()
            // This action deliberately changes no state, so there is no emission to wait
            // for — let the launched coroutine finish before checking what it did.
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify {
            addToShoppingList(
                AddToShoppingListUseCase.Params(productId = 3, sourceInventoryBatchId = batchId),
            )
        }
        coVerify(exactly = 0) { changeQuantity(any(), any()) }
        coVerify(exactly = 0) { markStatus(any(), any()) }
    }

    /**
     * Three cartons of milk with three dates were nowhere visible together before this:
     * the card showed the one batch that was tapped and nothing else.
     */
    @Test
    fun `the card lists the other packs of the same product, soonest first`() = runTest {
        val vm = viewModel()
        inventory.productBatches.value = listOf(
            InventoryItem(batch, product, fridge),
            InventoryItem(sibling(id = 8, expiry = LocalDate.of(2026, 8, 10)), product, fridge),
            InventoryItem(sibling(id = 9, expiry = LocalDate.of(2026, 8, 5)), product, fridge),
        )

        vm.uiState.test {
            val state = awaitItemWhere { it.otherBatches.isNotEmpty() }
            assertEquals(listOf(9L, 8L), state.otherBatches.map { it.batchId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A pack without a date is not an urgent one, so it goes to the end of the list. */
    @Test
    fun `packs without a date sort last`() = runTest {
        val vm = viewModel()
        inventory.productBatches.value = listOf(
            InventoryItem(sibling(id = 8, expiry = null), product, fridge),
            InventoryItem(sibling(id = 9, expiry = LocalDate.of(2026, 8, 5)), product, fridge),
        )

        vm.uiState.test {
            val state = awaitItemWhere { it.otherBatches.size == 2 }
            assertEquals(listOf(9L, 8L), state.otherBatches.map { it.batchId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The query returns everything ever recorded for the product. What was eaten or thrown
     * away is history, not stock — listing it as "also at home" would be a lie.
     */
    @Test
    fun `batches that are gone are not listed as still at home`() = runTest {
        val vm = viewModel()
        inventory.productBatches.value = listOf(
            InventoryItem(sibling(id = 8, expiry = LocalDate.of(2026, 8, 5)), product, fridge),
            InventoryItem(
                sibling(id = 9, expiry = LocalDate.of(2026, 8, 6)).copy(status = BatchStatus.CONSUMED),
                product,
                fridge,
            ),
            InventoryItem(
                sibling(id = 10, expiry = LocalDate.of(2026, 8, 7)).copy(deletedAt = clock.now()),
                product,
                fridge,
            ),
        )

        vm.uiState.test {
            val state = awaitItemWhere { it.otherBatches.isNotEmpty() }
            assertEquals(listOf(8L), state.otherBatches.map { it.batchId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the total counts this pack together with the others`() = runTest {
        val vm = viewModel()
        inventory.productBatches.value = listOf(
            InventoryItem(batch, product, fridge),
            InventoryItem(sibling(id = 8, expiry = LocalDate.of(2026, 8, 10)), product, fridge),
        )

        vm.uiState.test {
            val state = awaitItemWhere { it.total != null }
            // Two on the open card plus one more pack.
            assertEquals(3.0, state.total!!.quantity, 0.0)
            assertEquals(MeasurementUnit.PIECE, state.total.unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Half a litre plus one package is not a number; saying nothing beats inventing one. */
    @Test
    fun `no total is offered when the packs are measured differently`() = runTest {
        val vm = viewModel()
        inventory.productBatches.value = listOf(
            InventoryItem(batch, product, fridge),
            InventoryItem(
                sibling(id = 8, expiry = LocalDate.of(2026, 8, 10))
                    .copy(measurementUnit = MeasurementUnit.LITER),
                product,
                fridge,
            ),
        )

        vm.uiState.test {
            val state = awaitItemWhere { it.otherBatches.isNotEmpty() }
            assertNull(state.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a single pack is listed as no others and needs no total`() = runTest {
        val vm = viewModel()
        inventory.productBatches.value = listOf(InventoryItem(batch, product, fridge))

        vm.uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertTrue(state.otherBatches.isEmpty())
            assertNull(state.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun sibling(id: Long, expiry: LocalDate?) = InventoryBatch(
        id = id,
        productId = product.id,
        storageLocationId = fridge.id,
        quantity = 1.0,
        initialQuantity = 1.0,
        expirationDate = expiry,
    )

    @Test
    fun `moving the batch reports where it went`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.moveTo(9)

            assertEquals(R.string.event_moved, awaitItemWhere { it.undoableActionAt != null }.actionMessageRes)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { moveBatch(batchId, 9) }
    }

    /**
     * The state is a combine of five flows, so an action can surface across two or three
     * emissions. Waiting for the condition rather than a fixed number of items keeps the
     * tests from depending on how many intermediate states happen to be produced.
     */
    private suspend fun ReceiveTurbine<ProductUiState>.awaitItemWhere(
        predicate: (ProductUiState) -> Boolean,
    ): ProductUiState {
        repeat(MAX_EMISSIONS) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        throw AssertionError("No matching state after $MAX_EMISSIONS emissions")
    }

    private companion object {
        const val MAX_EMISSIONS = 20
    }
}
