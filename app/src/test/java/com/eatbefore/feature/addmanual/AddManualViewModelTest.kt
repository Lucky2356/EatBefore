package com.eatbefore.feature.addmanual

import androidx.lifecycle.SavedStateHandle
import com.eatbefore.R
import com.eatbefore.domain.catalog.CatalogContributor
import com.eatbefore.domain.catalog.CatalogProduct
import com.eatbefore.domain.catalog.CatalogSuggestions
import com.eatbefore.domain.catalog.ContributionResult
import com.eatbefore.domain.model.HomemadeKind
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.model.StorageType
import com.eatbefore.domain.repository.ProductRepository
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.domain.usecase.AddManualProductUseCase
import com.eatbefore.navigation.Routes
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
    private val suggestions = CatalogSuggestions()

    private val pantry = StorageLocation(id = 5, name = "Шкаф")
    private val fridge = StorageLocation(id = 1, name = "Холодильник", isDefault = true)
    private val freezer = StorageLocation(id = 7, name = "Морозилка", type = StorageType.FREEZER)

    private val locations = object : StorageLocationRepository {
        override fun observeActive(): Flow<List<StorageLocation>> = flowOf(listOf(pantry, fridge, freezer))
        override fun observeAll(): Flow<List<StorageLocation>> = flowOf(listOf(pantry, fridge, freezer))
        override suspend fun getById(id: Long) = fridge
        override suspend fun getDefault() = fridge
        override suspend fun setDefault(id: Long) = Unit
        override suspend fun upsert(location: StorageLocation) = fridge.id
    }

    private fun viewModel(
        barcode: String? = null,
        expiryEpochDay: Long? = null,
        catalogue: List<Product> = emptyList(),
        homemade: HomemadeKind? = null,
    ): AddManualViewModel {
        val args = mutableMapOf<String, Any?>()
        if (homemade != null) args[Routes.ADD_MANUAL_ARG_HOMEMADE] = homemade.name
        if (barcode != null) args[Routes.ADD_MANUAL_ARG_BARCODE] = barcode
        if (expiryEpochDay != null) args[Routes.ADD_MANUAL_ARG_EXPIRY] = expiryEpochDay
        val products = mockk<ProductRepository>()
        every { products.observeActive() } returns flowOf(catalogue)
        return AddManualViewModel(
            savedStateHandle = SavedStateHandle(args),
            addManualProduct = addManualProduct,
            catalogContributor = contributor,
            storageLocationRepository = locations,
            productRepository = products,
            catalogSuggestions = suggestions,
            clock = clock,
        )
    }

    /** From the scanner's «Проверить и добавить»: the guess is on the form, marked as one. */
    @Test
    fun `a catalog guess for the scanned code fills in the name and brand`() = runTest {
        suggestions.remember(
            CatalogProduct(barcode = "4607053473537", name = "Молоко Простоквашино", brand = "Простоквашино", needsReview = true),
        )

        val vm = viewModel(barcode = "4607053473537")
        advanceUntilIdle()

        assertEquals("Молоко Простоквашино", vm.state.value.name)
        assertEquals("Простоквашино", vm.state.value.brand)
        assertEquals("Молоко Простоквашино", vm.state.value.nameFromCatalog)
    }

    @Test
    fun `a saved guess is forgotten, the card answers for the code from now on`() = runTest {
        suggestions.remember(CatalogProduct(barcode = "4607053473537", name = "Milk", needsReview = true))
        coEvery { addManualProduct(any()) } returns 10L
        val vm = viewModel(barcode = "4607053473537")
        advanceUntilIdle()

        vm.onName("Молоко Простоквашино 3,2%")
        vm.save()
        advanceUntilIdle()

        assertNull(suggestions.forBarcode("4607053473537"))
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
        assertNull("an untouched category must not invent one", params.captured.category)
        assertEquals(2.0, params.captured.quantity, 0.0)
        assertEquals(LocalDate.of(2026, 8, 8), params.captured.expirationDate)
        assertEquals(11L, vm.state.value.savedBatchId)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun `the home-made button opens the form for a dish made today`() = runTest {
        val vm = viewModel(homemade = HomemadeKind.DISH)
        advanceUntilIdle()

        assertEquals(HomemadeKind.DISH, vm.state.value.homemadeKind)
        assertEquals(clock.today(), vm.state.value.cookedDate)
        // Before anything is typed: cooked food is three days in the fridge.
        assertEquals(3, vm.state.value.suggestedShelfLifeDays)
    }

    @Test
    fun `the ordinary form is not home-made`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.state.value.homemadeKind)
        assertNull(vm.state.value.cookedDate)
    }

    @Test
    fun `switching to a preserve gives a jar's shelf life`() = runTest {
        val vm = viewModel(homemade = HomemadeKind.DISH)
        advanceUntilIdle()

        vm.onHomemadeKind(HomemadeKind.PRESERVE)

        assertEquals(365, vm.state.value.suggestedShelfLifeDays)
    }

    @Test
    fun `a dish going into the freezer keeps for months`() = runTest {
        val vm = viewModel(homemade = HomemadeKind.DISH)
        advanceUntilIdle()

        vm.onLocation(freezer.id)

        assertEquals(90, vm.state.value.suggestedShelfLifeDays)
    }

    /**
     * Saved with its kind and the day it was made — not today, for a jar from last summer
     * — and without a barcode or brand, which the home-made form never shows.
     */
    @Test
    fun `saving home cooking passes its kind and the day it was made`() = runTest {
        coEvery { addManualProduct(any()) } returns 3L
        val vm = viewModel(homemade = HomemadeKind.PRESERVE)
        advanceUntilIdle()
        val madeLastSummer = LocalDate.of(2025, 8, 20)
        vm.onName("Огурцы маринованные")
        vm.onCookedDate(madeLastSummer)
        vm.onBarcode("4620017700531")

        vm.save()
        advanceUntilIdle()

        val params = slot<AddManualProductUseCase.Params>()
        coVerify { addManualProduct(capture(params)) }
        assertEquals(HomemadeKind.PRESERVE, params.captured.homemadeKind)
        assertEquals(madeLastSummer, params.captured.purchaseDate)
        assertNull(params.captured.barcode)
        assertNull("nothing to offer the shop catalogue", vm.state.value.contributeOffer)
    }

    /**
     * Took the hint, then set the jar's date to last summer: the date follows the hint
     * rather than staying a year from today under a chip that is no longer selected.
     */
    @Test
    fun `a date taken from the hint follows a later change of the day it was made`() = runTest {
        val vm = viewModel(homemade = HomemadeKind.PRESERVE)
        advanceUntilIdle()
        vm.onExpirationDate(clock.today().plusDays(365))

        val madeLastSummer = LocalDate.of(2025, 8, 20)
        vm.onCookedDate(madeLastSummer)

        assertEquals(madeLastSummer.plusDays(365), vm.state.value.expirationDate)
    }

    @Test
    fun `a date taken from the hint follows a switch from dish to preserve`() = runTest {
        val vm = viewModel(homemade = HomemadeKind.DISH)
        advanceUntilIdle()
        vm.onExpirationDate(clock.today().plusDays(3))

        vm.onHomemadeKind(HomemadeKind.PRESERVE)

        assertEquals(clock.today().plusDays(365), vm.state.value.expirationDate)
    }

    /** A date the user chose themselves is theirs, whatever happens to the hint. */
    @Test
    fun `a date the user picked is left alone`() = runTest {
        val vm = viewModel(homemade = HomemadeKind.DISH)
        advanceUntilIdle()
        val chosen = clock.today().plusDays(5)
        vm.onExpirationDate(chosen)

        vm.onCookedDate(clock.today().minusDays(1))
        vm.onHomemadeKind(HomemadeKind.PRESERVE)

        assertEquals(chosen, vm.state.value.expirationDate)
    }

    @Test
    fun `clearing the name does not take the chosen date with the hint`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onName("Молоко")
        val hinted = clock.today().plusDays(vm.state.value.suggestedShelfLifeDays!!.toLong())
        vm.onExpirationDate(hinted)

        vm.onName("")

        assertEquals(hinted, vm.state.value.expirationDate)
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

    /**
     * The fixed presets are the same for milk and for buckwheat. The suggestion is the
     * only chip that knows anything about this product, and it follows the name as it is
     * typed — by the time the expiry is chosen, the name is all the screen has.
     */
    @Test
    fun `a known product suggests how long it usually keeps`() = runTest {
        val vm = viewModel()

        vm.onName("Молоко 3,2%")

        assertEquals(7, vm.state.value.suggestedShelfLifeDays)
    }

    @Test
    fun `an unknown product suggests nothing rather than an average`() = runTest {
        val vm = viewModel()

        vm.onName("Вкусняшка")

        assertNull(vm.state.value.suggestedShelfLifeDays)
    }

    /** Nothing is applied on its own: a wrong date entered for the user would then be
     * warned about with complete confidence. */
    @Test
    fun `the suggestion does not set the date by itself`() = runTest {
        val vm = viewModel()

        vm.onName("Молоко")

        assertNull(vm.state.value.expirationDate)
    }

    /**
     * Until now the category could only be set afterwards, on the product card. Set here
     * it also sharpens the opening shelf-life guess, which the use case derives from the
     * name *and* the category.
     */
    @Test
    fun `a category typed on the form reaches the use case`() = runTest {
        coEvery { addManualProduct(any()) } returns 3L
        val vm = viewModel()
        advanceUntilIdle()
        vm.onName("Кефир")
        vm.onCategory("  Молочное  ")

        vm.save()
        advanceUntilIdle()

        val params = slot<AddManualProductUseCase.Params>()
        coVerify { addManualProduct(capture(params)) }
        assertEquals("  Молочное  ", params.captured.category)
    }

    /**
     * Offering what is already in use is the whole point: typed by hand every time, one
     * household ends up with «молочка», «Молочное» and «молоко» as three categories.
     * Case is the commonest way the same word comes back looking different.
     */
    @Test
    fun `known categories are offered once each, whatever the case`() = runTest {
        val vm = viewModel(
            catalogue = listOf(
                Product(name = "Кефир", category = "Молочное"),
                Product(name = "Сметана", category = "молочное"),
                Product(name = "Хлеб", category = "Выпечка"),
                Product(name = "Соль", category = null),
                Product(name = "Перец", category = "   "),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("Выпечка", "Молочное"), vm.state.value.knownCategories)
    }

    /** A category never used before is not a category the chips can offer. */
    @Test
    fun `an empty catalogue offers no categories`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.knownCategories.isEmpty())
    }

    /** Zero used to reach the use case, which refuses it — and the refusal crashed the app. */
    @Test
    fun `a zero amount is refused on the form, not by a crash`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onName("Сыр")
        vm.onQuantity("0")

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.quantityError)
        coVerify(exactly = 0) { addManualProduct(any()) }
    }

    /** «мол…» offers the milk already in the catalogue, and picking it fills the form. */
    @Test
    fun `typing offers known products and picking one fills the form`() = runTest {
        val milk = Product(
            id = 1,
            name = "Молоко 3,2%",
            brand = "Домик в деревне",
            category = "Молочное",
            measurementUnit = com.eatbefore.domain.model.MeasurementUnit.LITER,
        )
        val soup = Product(id = 2, name = "Молочный суп", homemadeKind = HomemadeKind.DISH)
        val vm = viewModel(catalogue = listOf(milk, soup))
        advanceUntilIdle()

        vm.onName("мол")
        assertEquals(listOf(1L), vm.state.value.nameSuggestions.map { it.productId })

        vm.onNameSuggestion(1L)
        val state = vm.state.value
        assertEquals("Молоко 3,2%", state.name)
        assertEquals("Домик в деревне", state.brand)
        assertEquals("Молочное", state.category)
        assertEquals(com.eatbefore.domain.model.MeasurementUnit.LITER, state.unit)
        assertTrue(state.nameSuggestions.isEmpty())
    }

    @Test
    fun `one letter suggests nothing`() = runTest {
        val vm = viewModel(catalogue = listOf(Product(id = 1, name = "Молоко")))
        advanceUntilIdle()

        vm.onName("м")

        assertTrue(vm.state.value.nameSuggestions.isEmpty())
    }
}
