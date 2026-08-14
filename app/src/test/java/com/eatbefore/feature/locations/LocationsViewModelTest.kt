package com.eatbefore.feature.locations

import app.cash.turbine.test
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType
import com.eatbefore.testutil.FakeStorageLocationRepository
import com.eatbefore.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Places are where every batch lives, so the guards here protect data rather than tidiness:
 * archiving the last place, or the default one, would leave adding a product with nowhere
 * to put it. Until now those guards were only ever exercised by hand.
 */
class LocationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fridge = StorageLocation(id = 1, name = "Холодильник", type = StorageType.FRIDGE, isDefault = true)
    private val shelf = StorageLocation(id = 2, name = "Шкаф", type = StorageType.CUPBOARD, sortOrder = 1)

    private fun viewModel(vararg initial: StorageLocation): Pair<LocationsViewModel, FakeStorageLocationRepository> {
        val repository = FakeStorageLocationRepository(initial.toList())
        return LocationsViewModel(repository) to repository
    }

    @Test
    fun `a new place goes to the end of the list`() = runTest {
        val (vm, repository) = viewModel(fridge, shelf)
        vm.locations.test {
            awaitItemWhere { it.isNotEmpty() }

            vm.add("Балкон", StorageType.OTHER)

            val added = awaitItemWhere { list -> list.any { it.name == "Балкон" } }.first { it.name == "Балкон" }
            assertEquals(2, added.sortOrder)
            assertEquals(StorageType.OTHER, added.type)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(3, repository.locations.value.size)
    }

    @Test
    fun `a blank name adds nothing`() = runTest {
        val (vm, repository) = viewModel(fridge)

        vm.add("   ", StorageType.OTHER)

        assertEquals(listOf(fridge), repository.locations.value)
    }

    @Test
    fun `renaming keeps the place, not a copy of it`() = runTest {
        val (vm, repository) = viewModel(fridge, shelf)
        vm.locations.test {
            awaitItemWhere { it.isNotEmpty() }

            vm.rename(shelf, "Кладовка")

            awaitItemWhere { list -> list.any { it.name == "Кладовка" } }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, repository.locations.value.size)
        assertEquals("Кладовка", repository.locations.value.first { it.id == shelf.id }.name)
    }

    /**
     * The default is where a quick add puts things. Archiving it would leave the scanner
     * with nowhere to write, and it would do so silently.
     */
    @Test
    fun `the default place cannot be archived`() = runTest {
        val (vm, repository) = viewModel(fridge, shelf)
        vm.locations.test {
            awaitItemWhere { it.size == 2 }

            assertFalse(vm.canArchive(fridge))
            vm.archive(fridge)

            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(repository.locations.value.first { it.id == fridge.id }.isArchived)
    }

    @Test
    fun `the last remaining place cannot be archived either`() = runTest {
        val onlyOne = shelf.copy(isDefault = false)
        val (vm, repository) = viewModel(onlyOne)
        vm.locations.test {
            awaitItemWhere { it.size == 1 }

            assertFalse(vm.canArchive(onlyOne))
            vm.archive(onlyOne)

            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(repository.locations.value.single().isArchived)
    }

    @Test
    fun `an ordinary place is archived and leaves the list`() = runTest {
        val (vm, repository) = viewModel(fridge, shelf)
        vm.locations.test {
            awaitItemWhere { it.size == 2 }

            assertTrue(vm.canArchive(shelf))
            vm.archive(shelf)

            assertEquals(listOf(fridge.id), awaitItemWhere { it.size == 1 }.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        // Archived, not deleted: batches stored there are still in that cupboard.
        assertTrue(repository.locations.value.first { it.id == shelf.id }.isArchived)
    }

    @Test
    fun `choosing a default leaves exactly one`() = runTest {
        val (vm, repository) = viewModel(fridge, shelf)
        vm.locations.test {
            awaitItemWhere { it.size == 2 }

            vm.setDefault(shelf.id)

            awaitItemWhere { list -> list.first { it.id == shelf.id }.isDefault }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(shelf.id), repository.locations.value.filter { it.isDefault }.map { it.id })
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<List<StorageLocation>>.awaitItemWhere(
        predicate: (List<StorageLocation>) -> Boolean,
    ): List<StorageLocation> {
        repeat(MAX_EMISSIONS) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching list within $MAX_EMISSIONS emissions")
    }

    private companion object {
        const val MAX_EMISSIONS = 20
    }
}
