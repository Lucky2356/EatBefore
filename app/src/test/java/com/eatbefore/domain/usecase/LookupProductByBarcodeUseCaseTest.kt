package com.eatbefore.domain.usecase

import com.eatbefore.domain.catalog.CatalogProduct
import com.eatbefore.domain.catalog.CatalogResult
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
        val useCase = LookupProductByBarcodeUseCase(products, catalog, clock)

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
        val useCase = LookupProductByBarcodeUseCase(products, catalog, clock)

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
        val useCase = LookupProductByBarcodeUseCase(products, catalog, clock)

        val result = useCase("123456789")
        assertTrue(result is BarcodeLookupResult.Error)
    }

    @Test
    fun notFound_isReported() = runTest {
        val products = FakeProductRepository()
        val catalog = StubCatalog(CatalogResult.NotFound)
        val useCase = LookupProductByBarcodeUseCase(products, catalog, clock)

        assertEquals(BarcodeLookupResult.NotFound, useCase("123456789"))
    }
}
