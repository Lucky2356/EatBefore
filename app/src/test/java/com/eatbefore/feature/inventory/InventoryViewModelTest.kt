package com.eatbefore.feature.inventory

import app.cash.turbine.test
import com.eatbefore.core.datastore.UserPreferences
import com.eatbefore.core.datastore.UserPreferencesRepository
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryItem
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.domain.usecase.DetermineExpiryStatusUseCase
import com.eatbefore.feature.common.QuickActions
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.FakeInventoryRepository
import com.eatbefore.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * The stock list answers two different questions — "what is in the freezer" and "what must
 * I deal with today" — and until now it could only answer the first.
 */
class InventoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeAppClock()
    private val today: LocalDate get() = clock.today()
    private val fridge = StorageLocation(id = 1, name = "Fridge", type = StorageType.FRIDGE)
    private val freezer = StorageLocation(id = 2, name = "Freezer", type = StorageType.FREEZER)
    private val inventory = FakeInventoryRepository()
    private val filterRequest = InventoryFilterRequest()

    /** Places come back in the order the user arranged them, as the real DAO returns them. */
    private val activeLocations = listOf(fridge, freezer)

    private val locations = object : StorageLocationRepository {
        override fun observeActive(): Flow<List<StorageLocation>> = flowOf(activeLocations)
        override fun observeAll(): Flow<List<StorageLocation>> = flowOf(activeLocations)
        override suspend fun getById(id: Long) = fridge
        override suspend fun getDefault() = fridge
        override suspend fun setDefault(id: Long) = Unit
        override suspend fun upsert(location: StorageLocation) = fridge.id
    }

    private fun item(
        id: Long,
        name: String,
        expiry: LocalDate?,
        opened: Boolean = false,
        location: StorageLocation = fridge,
    ) = InventoryItem(
        batch = InventoryBatch(
            id = id,
            productId = id,
            storageLocationId = location.id,
            quantity = 1.0,
            initialQuantity = 1.0,
            expirationDate = expiry,
            openedAt = if (opened) Instant.EPOCH else null,
        ),
        product = Product(id = id, name = name),
        location = location,
    )

    private fun viewModel(): InventoryViewModel {
        val prefs = mockk<UserPreferencesRepository>()
        every { prefs.preferences } returns flowOf(UserPreferences(soonThresholdDays = 3))
        return InventoryViewModel(
            inventoryRepository = inventory,
            storageLocationRepository = locations,
            preferences = prefs,
            determineExpiryStatus = DetermineExpiryStatusUseCase(),
            quickActions = mockk<QuickActions>(relaxed = true),
            filterRequest = filterRequest,
            clock = clock,
        )
    }

    private fun seed() {
        inventory.presentItems.value = listOf(
            item(1, "Просроченное", today.minusDays(2)),
            item(2, "Скоро", today.plusDays(1)),
            item(3, "Свежее", today.plusDays(30)),
            item(4, "Вскрытое", today.plusDays(30), opened = true),
        )
    }

    @Test
    fun `by default every present batch is listed`() = runTest {
        seed()

        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertEquals(4, state.rows.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the expired filter keeps only what is past its date`() = runTest {
        seed()
        val vm = viewModel()

        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.setStatusFilter(InventoryStatusFilter.EXPIRED)

            val state = awaitItemWhere { it.statusFilter == InventoryStatusFilter.EXPIRED }
            assertEquals(listOf("Просроченное"), state.rows.map { it.productName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the opened filter keeps only started packages`() = runTest {
        seed()
        val vm = viewModel()

        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.setStatusFilter(InventoryStatusFilter.OPENED)

            val state = awaitItemWhere { it.statusFilter == InventoryStatusFilter.OPENED }
            assertEquals(listOf("Вскрытое"), state.rows.map { it.productName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** What the reminder warns about today, including the last day itself. */
    @Test
    fun `the soon filter keeps what is running out but not what has gone off`() = runTest {
        inventory.presentItems.value = listOf(
            item(1, "Просроченное", today.minusDays(1)),
            item(2, "Сегодня", today),
            item(3, "Скоро", today.plusDays(2)),
            item(4, "Свежее", today.plusDays(30)),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.setStatusFilter(InventoryStatusFilter.SOON)

            val state = awaitItemWhere { it.statusFilter == InventoryStatusFilter.SOON }
            assertEquals(listOf("Сегодня", "Скоро"), state.rows.map { it.productName }.sorted())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The home screen's "expired" tile asks for this before switching tabs. */
    @Test
    fun `a requested filter is applied and then forgotten`() = runTest {
        seed()
        filterRequest.request(InventoryStatusFilter.EXPIRED)
        val vm = viewModel()

        vm.uiState.test {
            val state = awaitItemWhere { it.statusFilter == InventoryStatusFilter.EXPIRED }
            assertEquals(listOf("Просроченное"), state.rows.map { it.productName })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(null, filterRequest.pending.value)
    }

    @Test
    fun `rows are grouped by place in the order the places are arranged`() = runTest {
        inventory.presentItems.value = listOf(
            item(1, "Пельмени", today.plusDays(30), location = freezer),
            item(2, "Молоко", today.plusDays(2)),
        )

        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading && it.groups.isNotEmpty() }
            assertEquals(listOf("Fridge", "Freezer"), state.groups.map { it.location.name })
            assertEquals(listOf("Молоко"), state.groups.first().rows.map { it.productName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** With one place chosen the heading would only repeat the chip above it. */
    @Test
    fun `choosing a place turns the headings off`() = runTest {
        inventory.presentItems.value = listOf(item(1, "Молоко", today.plusDays(2)))
        val vm = viewModel()

        vm.uiState.test {
            assertTrue(awaitItemWhere { !it.isLoading && it.rows.isNotEmpty() }.isGrouped)
            vm.setLocation(fridge.id)

            assertFalse(awaitItemWhere { it.selectedLocationId == fridge.id }.isGrouped)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A batch in a place archived after the fact is still in that fridge in real life.
     * Dropping it from the list would hide food the household owns.
     */
    @Test
    fun `a batch in an archived place still appears, last`() = runTest {
        val gone = StorageLocation(id = 99, name = "Балкон", type = StorageType.OTHER)
        inventory.presentItems.value = listOf(
            item(1, "Заготовки", today.plusDays(10), location = gone),
            item(2, "Молоко", today.plusDays(2)),
        )

        viewModel().uiState.test {
            val state = awaitItemWhere { !it.isLoading && it.groups.isNotEmpty() }
            assertEquals(listOf("Fridge", "Балкон"), state.groups.map { it.location.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The note is the one field holding the user's own words, so it must be searchable. */
    @Test
    fun `search looks inside the note as well as the name`() = runTest {
        inventory.presentItems.value = listOf(
            item(1, "Мука", today.plusDays(30)).let {
                it.copy(batch = it.batch.copy(note = "для пирога"))
            },
            item(2, "Сахар", today.plusDays(30)),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.setQuery("пирог")

            val state = awaitItemWhere { it.rows.size == 1 }
            assertEquals(listOf("Мука"), state.rows.map { it.productName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching back to all restores the whole list`() = runTest {
        seed()
        val vm = viewModel()

        vm.uiState.test {
            awaitItemWhere { !it.isLoading }
            vm.setStatusFilter(InventoryStatusFilter.EXPIRED)
            awaitItemWhere { it.statusFilter == InventoryStatusFilter.EXPIRED }
            vm.setStatusFilter(InventoryStatusFilter.ALL)

            val state = awaitItemWhere { it.statusFilter == InventoryStatusFilter.ALL }
            assertEquals(4, state.rows.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/**
 * Awaits the first item matching [predicate]. State flows conflate and replay, so a test
 * cannot assume the interesting state is the very next emission.
 */
private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitItemWhere(predicate: (T) -> Boolean): T {
    repeat(MAX_EMISSIONS) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
    error("No matching item within $MAX_EMISSIONS emissions")
}

private const val MAX_EMISSIONS = 20
