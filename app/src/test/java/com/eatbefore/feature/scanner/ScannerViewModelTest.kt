package com.eatbefore.feature.scanner

import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.StorageLocation
import com.eatbefore.domain.repository.StorageLocationRepository
import com.eatbefore.domain.usecase.AddBatchUseCase
import com.eatbefore.domain.usecase.BarcodeLookupResult
import com.eatbefore.domain.usecase.LookupProductByBarcodeUseCase
import com.eatbefore.feature.scanner.camera.ScannedCode
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
 * The scanner is the one screen driven by a stream of events the user does not control:
 * ML Kit reports the same barcode many times a second. Most of the logic here exists to
 * turn that stream into one action, and none of it was covered.
 */
class ScannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeAppClock(Instant.parse("2026-08-01T10:00:00Z"))
    private val lookup = mockk<LookupProductByBarcodeUseCase>()
    private val addBatch = mockk<AddBatchUseCase>(relaxed = true)

    private val milk = Product(id = 3, name = "Молоко")
    private val fridge = StorageLocation(id = 1, name = "Холодильник", isDefault = true)
    private var defaultLocation: StorageLocation? = fridge

    private val locations = object : StorageLocationRepository {
        override fun observeActive(): Flow<List<StorageLocation>> = flowOf(listOf(fridge))
        override fun observeAll(): Flow<List<StorageLocation>> = flowOf(listOf(fridge))
        override suspend fun getById(id: Long) = fridge
        override suspend fun getDefault() = defaultLocation
        override suspend fun setDefault(id: Long) = Unit
        override suspend fun upsert(location: StorageLocation) = fridge.id
    }

    private fun viewModel() = ScannerViewModel(lookup, addBatch, locations, clock)

    private fun scan(value: String, type: BarcodeType = BarcodeType.EAN_13) = ScannedCode(value, type)

    @Test
    fun `a known code resolves to the product`() = runTest {
        coEvery { lookup(any(), any()) } returns BarcodeLookupResult.Found(milk, fromNetwork = true)
        val vm = viewModel()

        vm.onCodeDetected(scan("4620017700531"))
        advanceUntilIdle()

        val resolution = vm.state.value.resolution
        assertTrue(resolution is ScanResolution.Found)
        assertEquals(milk, (resolution as ScanResolution.Found).product)
        assertFalse(vm.state.value.isResolving)
    }

    @Test
    fun `an unknown code is reported as not found, not as an error`() = runTest {
        coEvery { lookup(any(), any()) } returns BarcodeLookupResult.NotFound
        val vm = viewModel()

        vm.onCodeDetected(scan("0000000000000"))
        advanceUntilIdle()

        assertTrue(vm.state.value.resolution is ScanResolution.NotFound)
    }

    /** A thrown lookup must surface as an error card, not take the scanner down. */
    @Test
    fun `a lookup that throws becomes an error result`() = runTest {
        coEvery { lookup(any(), any()) } throws IllegalStateException("сеть недоступна")
        val vm = viewModel()

        vm.onCodeDetected(scan("4620017700531"))
        advanceUntilIdle()

        val resolution = vm.state.value.resolution
        assertTrue(resolution is ScanResolution.Error)
        assertEquals("сеть недоступна", (resolution as ScanResolution.Error).message)
    }

    /**
     * ML Kit fires continuously while a code is in frame. In batch mode nothing stops to
     * ask the user, so without the debounce one packet held in front of the camera would
     * be added dozens of times a second.
     */
    @Test
    fun `batch mode adds a packet held in frame only once`() = runTest {
        coEvery { lookup(any(), any()) } returns BarcodeLookupResult.Found(milk, fromNetwork = false)
        coEvery { addBatch(any()) } returns 1L
        val vm = viewModel()
        vm.setBatchMode(true)

        repeat(5) {
            vm.onCodeDetected(scan("4620017700531"))
            advanceUntilIdle()
        }

        assertEquals(1, vm.state.value.batchAddedCount)
        coVerify(exactly = 1) { addBatch(any()) }
    }

    /** Two identical packets are a normal purchase: after the window, count them both. */
    @Test
    fun `batch mode counts the same code again once the window has passed`() = runTest {
        coEvery { lookup(any(), any()) } returns BarcodeLookupResult.Found(milk, fromNetwork = false)
        coEvery { addBatch(any()) } returns 1L
        val vm = viewModel()
        vm.setBatchMode(true)

        vm.onCodeDetected(scan("4620017700531"))
        advanceUntilIdle()
        clock.set(Instant.parse("2026-08-01T10:00:05Z"))
        vm.onCodeDetected(scan("4620017700531"))
        advanceUntilIdle()

        assertEquals(2, vm.state.value.batchAddedCount)
    }

    /**
     * Dismissing the card is an explicit "I am done with that one", so the very same code
     * has to be accepted again immediately — otherwise re-scanning an item the user just
     * cancelled would silently do nothing for three seconds.
     */
    @Test
    fun `dismissing the card allows the same code straight away`() = runTest {
        coEvery { lookup(any(), any()) } returns BarcodeLookupResult.Found(milk, fromNetwork = false)
        val vm = viewModel()

        vm.onCodeDetected(scan("4620017700531"))
        advanceUntilIdle()
        vm.resume()
        vm.onCodeDetected(scan("4620017700531"))
        advanceUntilIdle()

        coVerify(exactly = 2) { lookup(any(), any()) }
    }

    /** While a result card is on screen the camera must not resolve anything behind it. */
    @Test
    fun `codes seen while a result is showing are ignored`() = runTest {
        coEvery { lookup(any(), any()) } returns BarcodeLookupResult.Found(milk, fromNetwork = false)
        val vm = viewModel()

        vm.onCodeDetected(scan("4620017700531"))
        advanceUntilIdle()
        vm.onCodeDetected(scan("4600000000000"))
        advanceUntilIdle()

        coVerify(exactly = 1) { lookup(any(), any()) }
    }

    /**
     * A «Честный знак» DataMatrix carries the GTIN and the expiry date inside the payload.
     * The lookup has to use the extracted GTIN, not the whole marking string, and the date
     * must reach the batch so the user types nothing.
     */
    @Test
    fun `a GS1 marking is looked up by its GTIN and carries the date`() = runTest {
        coEvery { lookup(any(), any()) } returns BarcodeLookupResult.Found(milk, fromNetwork = false)
        val vm = viewModel()

        vm.onCodeDetected(scan("01046200177005311721123117", BarcodeType.DATA_MATRIX))
        advanceUntilIdle()

        val resolution = vm.state.value.resolution!!
        assertEquals("4620017700531", resolution.code)
        assertEquals(LocalDate.of(2021, 12, 31), resolution.expiryFromCode)
        coVerify { lookup("4620017700531", BarcodeType.DATA_MATRIX) }
    }

    @Test
    fun `adding one package uses the default location and the date from the code`() = runTest {
        coEvery { addBatch(any()) } returns 42L
        val vm = viewModel()

        vm.addOnePackage(milk, LocalDate.of(2026, 8, 10))
        advanceUntilIdle()

        coVerify {
            addBatch(
                AddBatchUseCase.Params(
                    productId = 3,
                    storageLocationId = 1,
                    quantity = 1.0,
                    expirationDate = LocalDate.of(2026, 8, 10),
                ),
            )
        }
        // The card must clear itself: leaving it up swallows taps meant for the camera.
        assertEquals(42L, vm.state.value.addedBatchId)
        assertNull(vm.state.value.resolution)
        assertTrue(vm.state.value.isScanning)
    }

    /** No default location means nothing can be added; the screen must not hang resolving. */
    @Test
    fun `adding without a default location stops resolving instead of hanging`() = runTest {
        defaultLocation = null
        val vm = viewModel()

        vm.addOnePackage(milk, null)
        advanceUntilIdle()

        assertFalse(vm.state.value.isResolving)
        coVerify(exactly = 0) { addBatch(any()) }
    }

    /** Batch mode: a known product goes straight into stock and the camera stays live. */
    @Test
    fun `batch mode adds a known product without a dialog and keeps scanning`() = runTest {
        coEvery { lookup(any(), any()) } returns BarcodeLookupResult.Found(milk, fromNetwork = false)
        coEvery { addBatch(any()) } returns 1L
        val vm = viewModel()
        vm.setBatchMode(true)

        vm.onCodeDetected(scan("4620017700531"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.batchAddedCount)
        assertTrue(state.isScanning)
        assertNull("no card must interrupt the run", state.resolution)
    }

    /** Unknown codes are parked rather than dropped, so nothing scanned is silently lost. */
    @Test
    fun `batch mode parks unknown codes for the end of the run`() = runTest {
        coEvery { lookup(any(), any()) } returns BarcodeLookupResult.NotFound
        val vm = viewModel()
        vm.setBatchMode(true)

        vm.onCodeDetected(scan("0000000000000"))
        advanceUntilIdle()

        assertEquals(listOf("0000000000000"), vm.state.value.batchUnknownCodes)
        assertTrue(vm.state.value.isScanning)
    }

    @Test
    fun `the same unknown code is parked once`() = runTest {
        coEvery { lookup(any(), any()) } returns BarcodeLookupResult.NotFound
        val vm = viewModel()
        vm.setBatchMode(true)

        vm.onCodeDetected(scan("0000000000000"))
        advanceUntilIdle()
        clock.set(Instant.parse("2026-08-01T10:00:05Z"))
        vm.onCodeDetected(scan("0000000000000"))
        advanceUntilIdle()

        assertEquals(listOf("0000000000000"), vm.state.value.batchUnknownCodes)
    }

    @Test
    fun `a parked code is dropped once it has been dealt with`() = runTest {
        coEvery { lookup(any(), any()) } returns BarcodeLookupResult.NotFound
        val vm = viewModel()
        vm.setBatchMode(true)
        vm.onCodeDetected(scan("0000000000000"))
        advanceUntilIdle()

        vm.consumeUnknownCode("0000000000000")

        assertTrue(vm.state.value.batchUnknownCodes.isEmpty())
    }

    /** Leaving batch mode must not carry the previous run's tally into the next one. */
    @Test
    fun `switching batch mode resets the run`() = runTest {
        coEvery { lookup(any(), any()) } returns BarcodeLookupResult.Found(milk, fromNetwork = false)
        coEvery { addBatch(any()) } returns 1L
        val vm = viewModel()
        vm.setBatchMode(true)
        vm.onCodeDetected(scan("4620017700531"))
        advanceUntilIdle()

        vm.setBatchMode(false)

        assertEquals(0, vm.state.value.batchAddedCount)
        assertTrue(vm.state.value.batchUnknownCodes.isEmpty())
    }

    @Test
    fun `the torch toggles`() = runTest {
        val vm = viewModel()

        vm.toggleTorch()
        assertTrue(vm.state.value.torchEnabled)

        vm.toggleTorch()
        assertFalse(vm.state.value.torchEnabled)
    }
}
