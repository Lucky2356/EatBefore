package com.eatbefore.domain.usecase

import com.eatbefore.domain.catalog.CatalogProduct
import com.eatbefore.domain.catalog.CatalogResult
import com.eatbefore.domain.catalog.CatalogSuggestions
import com.eatbefore.domain.catalog.ProductCatalogProvider
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.ProductSource
import com.eatbefore.testutil.FakeAppClock
import com.eatbefore.testutil.FakeProductRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupProductByBarcodeUseCaseTest {

    private val clock = FakeAppClock()

    private class StubCatalog(var result: CatalogResult) : ProductCatalogProvider {
        var calls = 0
        override suspend fun lookupByBarcode(barcode: String): CatalogResult {
            calls++
            return result
        }
    }

    @Test
    fun localCacheHit_skipsNetwork() = runTest {
        val products = FakeProductRepository()
        products.upsert(
            Product(barcode = "4600000000017", name = "Water", source = ProductSource.SCAN_CACHE, isUserCreated = false),
        )
        val catalog = StubCatalog(CatalogResult.NotFound)
        val useCase = LookupProductByBarcodeUseCase(products, catalog, CatalogSuggestions(), clock)

        val result = useCase("4600000000017")
        assertTrue(result is BarcodeLookupResult.Found)
        assertFalse((result as BarcodeLookupResult.Found).fromNetwork)
        assertEquals(0, catalog.calls)
    }

    @Test
    fun networkFound_cachesProduct() = runTest {
        val products = FakeProductRepository()
        val catalog = StubCatalog(
            CatalogResult.Found(CatalogProduct(barcode = "123456789", name = "Milk", brand = "Farm")),
        )
        val useCase = LookupProductByBarcodeUseCase(products, catalog, CatalogSuggestions(), clock)

        val result = useCase("123456789")
        assertTrue(result is BarcodeLookupResult.Found)
        assertTrue((result as BarcodeLookupResult.Found).fromNetwork)
        // Cached locally, so a second lookup no longer hits the network.
        val second = useCase("123456789")
        assertTrue(second is BarcodeLookupResult.Found)
        assertFalse((second as BarcodeLookupResult.Found).fromNetwork)
        assertEquals(1, catalog.calls)
    }

    @Test
    fun networkError_isReported() = runTest {
        val products = FakeProductRepository()
        val catalog = StubCatalog(CatalogResult.Error("offline"))
        val useCase = LookupProductByBarcodeUseCase(products, catalog, CatalogSuggestions(), clock)

        val result = useCase("123456789")
        assertTrue(result is BarcodeLookupResult.Error)
    }

    @Test
    fun notFound_isReported() = runTest {
        val products = FakeProductRepository()
        val catalog = StubCatalog(CatalogResult.NotFound)
        val useCase = LookupProductByBarcodeUseCase(products, catalog, CatalogSuggestions(), clock)

        assertEquals(BarcodeLookupResult.NotFound, useCase("123456789"))
    }

    /**
     * A name that needs a look is not saved on the spot: a card made from «Кефир Купить В
     * Алматы» would carry it for good, and the scanner's quick add would do exactly that.
     */
    @Test
    fun aHitThatNeedsReview_isSuggestedAndNotSaved() = runTest {
        val products = FakeProductRepository()
        val guess = CatalogProduct(barcode = "4607053473537", name = "Milk Prostokvashino", needsReview = true)
        val suggestions = CatalogSuggestions()
        val useCase = LookupProductByBarcodeUseCase(products, StubCatalog(CatalogResult.Found(guess)), suggestions, clock)

        assertEquals(BarcodeLookupResult.Suggested(guess), useCase("4607053473537"))
        assertEquals(null, products.getByBarcode("4607053473537"))
        // Left for the add form to pick up.
        assertEquals(guess, suggestions.forBarcode("4607053473537"))
    }

    /** The source allows 150 requests a month; scanning the same packet again is free. */
    @Test
    fun aSecondScanOfASuggestedCode_doesNotAskAgain() = runTest {
        val guess = CatalogProduct(barcode = "4607053473537", name = "Milk", needsReview = true)
        val catalog = StubCatalog(CatalogResult.Found(guess))
        val useCase = LookupProductByBarcodeUseCase(FakeProductRepository(), catalog, CatalogSuggestions(), clock)

        useCase("4607053473537")
        val second = useCase("4607053473537")

        assertEquals(BarcodeLookupResult.Suggested(guess), second)
        assertEquals(1, catalog.calls)
    }
}
