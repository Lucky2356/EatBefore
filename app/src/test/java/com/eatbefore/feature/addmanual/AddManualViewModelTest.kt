package com.eatbefore.feature.addmanual

import androidx.lifecycle.SavedStateHandle
import com.eatbefore.R
import com.eatbefore.domain.catalog.CatalogContributor
import com.eatbefore.domain.catalog.CatalogProduct
import com.eatbefore.domain.catalog.ContributionResult
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.domain.usecase.AddManualProductUseCase
import com.eatbefore.navigation.Routes
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Manual entry is the fallback for everything the catalog does not know, which in Russia
 * is a great deal. Two things here are easy to get wrong and impossible to see from the
 * outside: sending a product to the shared catalog without being asked, and navigating
 * away before the user has answered that question.
 */
class AddManualViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeAppClock(Instant.parse("2026-08-01T10:00:00Z"))
    private val addManualProduct = mockk<AddManualProductUseCase>()
    private val contributor = mockk<CatalogContributor>(relaxed = true)

    private val pantry = StorageLocation(id = 5, name = "Шкаф")
    private val fridge = StorageLocation(id = 1, name = "Холодильник", isDefault = true)

    private val locations = object : StorageLocationRepository {
        override fun observeActive(): Flow<List<StorageLocation>> = flowOf(listOf(pantry, fridge))
        override fun observeAll(): Flow<List<StorageLocation>> = flowOf(listOf(pantry, fridge))
        override suspend fun getById(id: Long) = fridge
        override suspend fun getDefault() = fridge
        override suspend fun setDefault(id: Long) = Unit
        override suspend fun upsert(location: StorageLocation) = fridge.id
    }

    private fun viewModel(
        barcode: String? = null,
        expiryEpochDay: Long? = null,
    ): AddManualViewModel {
        val args = mutableMapOf<String, Any?>()
        if (barcode != null) args[Routes.ADD_MANUAL_ARG_BARCODE] = barcode
        if (expiryEpochDay != null) args[Routes.ADD_MANUAL_ARG_EXPIRY] = expiryEpochDay
        return AddManualViewModel(
            savedStateHandle = SavedStateHandle(args),
            addManualProduct = addManualProduct,
            catalogContributor = contributor,
            storageLocationRepository = locations,
            clock = clock,
        )
    }

    /** The default location must win, not simply the first one in the list. */
    @Test
    fun `the default storage location is preselected`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(fridge.id, vm.state.value.selectedLocationId)
    }

    @Test
    fun `a location the user picked is not overwritten by the default`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onLocation(pantry.id)
        advanceUntilIdle()

        assertEquals(pantry.id, vm.state.value.selectedLocationId)
    }

    /** Arriving from a «Честный знак» code: the date is already known, so prefill it. */
    @Test
    fun `an expiry date from the scanned code prefills the form`() = runTest {
        val date = LocalDate.of(2026, 9, 15)
        val vm = viewModel(barcode = "4620017700531", expiryEpochDay = date.toEpochDay())

        assertEquals(date, vm.state.value.expirationDate)
    }

    @Test
    fun `an absent expiry argument leaves the date empty`() = runTest {
        val vm = viewModel(barcode = "4620017700531", expiryEpochDay = -1)

        assertNull(vm.state.value.expirationDate)
    }

    @Test
    fun `saving without a name is refused and flags the field`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.nameError)
        assertNull(vm.state.value.savedBatchId)
        coVerify(exactly = 0) { addManualProduct(any()) }
    }

    @Test
    fun `typing a name clears the error`() = runTest {
        val vm = viewModel()
        vm.save()
        advanceUntilIdle()

        vm.onName("Гречка")

        assertFalse(vm.state.value.nameError)
    }

    @Test
    fun `saving passes the form through and reports the new batch`() = runTest {
        coEvery { addManualProduct(any()) } returns 11L
        val vm = viewModel()
        advanceUntilIdle()
        vm.onName("Гречка")
        vm.onBrand("Мистраль")
        vm.onQuantity("2")
        vm.onQuickExpiry(7)

        vm.save()
        advanceUntilIdle()

        val params = slot<AddManualProductUseCase.Params>()
        coVerify { addManualProduct(capture(params)) }
        assertEquals("Гречка", params.captured.name)
        assertEquals("Мистраль", params.captured.brand)
        assertEquals(2.0, params.captured.quantity, 0.0)
        assertEquals(LocalDate.of(2026, 8, 8), params.captured.expirationDate)
        assertEquals(11L, vm.state.value.savedBatchId)
        assertFalse(vm.state.value.isSaving)
    }

    /** Letters in a number field would otherwise reach the parser and silently become 1. */
    @Test
    fun `the quantity field keeps only digits and a decimal point`() = runTest {
        val vm = viewModel()

        vm.onQuantity("1a.5кг")

        assertEquals("1.5", vm.state.value.quantity)
    }

    @Test
    fun `the stepper moves the quantity by one`() = runTest {
        val vm = viewModel()

        vm.stepQuantity(+1)

        assertEquals("2", vm.state.value.quantity)
    }

    /** Zero packages of something is not a thing anyone adds to an inventory. */
    @Test
    fun `the stepper never goes below one`() = runTest {
        val vm = viewModel()

        vm.stepQuantity(-1)
        vm.stepQuantity(-1)

        assertEquals("1", vm.state.value.quantity)
    }

    /** A whole number must not come back as "3.0" in a field the user types into. */
    @Test
    fun `the stepper keeps whole amounts free of a decimal tail`() = runTest {
        val vm = viewModel()

        vm.onQuantity("2.5")
        vm.stepQuantity(+1)

        assertEquals("3.5", vm.state.value.quantity)
        vm.onQuantity("2")
        vm.stepQuantity(+1)
        assertEquals("3", vm.state.value.quantity)
    }

    /**
     * Nothing may be published without being asked. A product entered by hand, with no
     * barcode, has nothing to contribute anyway — and must not raise the question.
     */
    @Test
    fun `a product without a barcode is never offered to the catalog`() = runTest {
        coEvery { addManualProduct(any()) } returns 1L
        coEvery { contributor.isConfigured() } returns true
        val vm = viewModel(barcode = null)
        advanceUntilIdle()
        vm.onName("Гречка")

        vm.save()
        advanceUntilIdle()

        assertNull(vm.state.value.contributeOffer)
    }

    /** Without a linked account there is nothing to offer: the catalog refuses anonymous edits. */
    @Test
    fun `no offer is made when no catalog account is linked`() = runTest {
        coEvery { addManualProduct(any()) } returns 1L
        coEvery { contributor.isConfigured() } returns false
        val vm = viewModel(barcode = "4620017700531")
        advanceUntilIdle()
        vm.onName("Гречка")

        vm.save()
        advanceUntilIdle()

        assertNull(vm.state.value.contributeOffer)
    }

    @Test
    fun `an unknown barcode with a linked account raises the offer`() = runTest {
        coEvery { addManualProduct(any()) } returns 1L
        coEvery { contributor.isConfigured() } returns true
        val vm = viewModel(barcode = "4620017700531")
        advanceUntilIdle()
        vm.onName("  Гречка  ")

        vm.save()
        advanceUntilIdle()

        val offer = vm.state.value.contributeOffer!!
        assertEquals("Гречка", offer.name)
        assertEquals("4620017700531", offer.barcode)
    }

    /** Declining must send nothing at all — this is the whole point of asking. */
    @Test
    fun `declining the offer sends nothing`() = runTest {
        coEvery { addManualProduct(any()) } returns 1L
        coEvery { contributor.isConfigured() } returns true
        val vm = viewModel(barcode = "4620017700531")
        advanceUntilIdle()
        vm.onName("Гречка")
        vm.save()
        advanceUntilIdle()

        vm.declineContribution()
        advanceUntilIdle()

        assertNull(vm.state.value.contributeOffer)
        coVerify(exactly = 0) { contributor.contribute(any()) }
    }

    @Test
    fun `confirming sends the barcode, the name and the brand and nothing else`() = runTest {
        coEvery { addManualProduct(any()) } returns 1L
        coEvery { contributor.isConfigured() } returns true
        coEvery { contributor.contribute(any()) } returns ContributionResult.Success
        val vm = viewModel(barcode = "4620017700531")
        advanceUntilIdle()
        vm.onName("Гречка")
        vm.onBrand("Мистраль")
        vm.onNote("в дальнем углу")
        vm.save()
        advanceUntilIdle()

        vm.confirmContribution()
        advanceUntilIdle()

        val sent = slot<CatalogProduct>()
        coVerify { contributor.contribute(capture(sent)) }
        assertEquals("4620017700531", sent.captured.barcode)
        assertEquals("Гречка", sent.captured.name)
        assertEquals("Мистраль", sent.captured.brand)
        assertNull("stock details must not be published", sent.captured.packageSize)
        assertEquals(R.string.contribute_success, vm.state.value.message)
        assertNull(vm.state.value.contributeOffer)
    }

    @Test
    fun `a rejected login is reported as such, not as a generic failure`() = runTest {
        coEvery { addManualProduct(any()) } returns 1L
        coEvery { contributor.isConfigured() } returns true
        coEvery { contributor.contribute(any()) } returns ContributionResult.AuthFailed
        val vm = viewModel(barcode = "4620017700531")
        advanceUntilIdle()
        vm.onName("Гречка")
        vm.save()
        advanceUntilIdle()

        vm.confirmContribution()
        advanceUntilIdle()

        assertEquals(R.string.contribute_auth_failed, vm.state.value.message)
        assertFalse(vm.state.value.isContributing)
    }

    @Test
    fun `a failed send is reported and leaves the screen usable`() = runTest {
        coEvery { addManualProduct(any()) } returns 1L
        coEvery { contributor.isConfigured() } returns true
        coEvery { contributor.contribute(any()) } returns ContributionResult.Failed("timeout")
        val vm = viewModel(barcode = "4620017700531")
        advanceUntilIdle()
        vm.onName("Гречка")
        vm.save()
        advanceUntilIdle()

        vm.confirmContribution()
        advanceUntilIdle()

        assertEquals(R.string.contribute_failed, vm.state.value.message)
        assertFalse(vm.state.value.isContributing)
        assertNull(vm.state.value.contributeOffer)
    }

    @Test
    fun `the message is shown once`() = runTest {
        coEvery { addManualProduct(any()) } returns 1L
        coEvery { contributor.isConfigured() } returns true
        coEvery { contributor.contribute(any()) } returns ContributionResult.Success
        val vm = viewModel(barcode = "4620017700531")
        advanceUntilIdle()
        vm.onName("Гречка")
        vm.save()
        advanceUntilIdle()
        vm.confirmContribution()
        advanceUntilIdle()

        vm.consumeMessage()

        assertNull(vm.state.value.message)
    }

    @Test
    fun `clearing the quick expiry removes the date`() = runTest {
        val vm = viewModel()
        vm.onQuickExpiry(3)

        vm.onQuickExpiry(null)

        assertNull(vm.state.value.expirationDate)
    }
}
