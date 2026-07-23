package com.eatbefore.data.catalog

import com.eatbefore.domain.catalog.CatalogProduct
import com.eatbefore.domain.catalog.CatalogResult
import com.eatbefore.domain.catalog.ProductCatalogProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainedCatalogProviderTest {

    private class Fake(private val result: CatalogResult) : ProductCatalogProvider {
        var calls = 0
        override suspend fun lookupByBarcode(barcode: String): CatalogResult {
            calls++
            return result
        }
    }

    private val found = CatalogResult.Found(CatalogProduct(barcode = "1", name = "Молоко"))

    @Test
    fun `returns the first hit and stops asking further sources`() = runTest {
        val first = Fake(found)
        val second = Fake(CatalogResult.NotFound)

        val result = ChainedCatalogProvider(listOf(first, second)).lookupByBarcode("1")

        assertEquals(found, result)
        assertEquals(1, first.calls)
        // The second source costs a network round-trip; it must not be touched.
        assertEquals(0, second.calls)
    }

    @Test
    fun `keeps looking after a source does not know the code`() = runTest {
        val second = Fake(found)

        val result = ChainedCatalogProvider(listOf(Fake(CatalogResult.NotFound), second)).lookupByBarcode("1")

        assertEquals(found, result)
        assertEquals(1, second.calls)
    }

    @Test
    fun `a broken source does not hide a product the next one knows`() = runTest {
        val result = ChainedCatalogProvider(
            listOf(Fake(CatalogResult.Error("HTTP 503")), Fake(found)),
        ).lookupByBarcode("1")

        assertEquals(found, result)
    }

    @Test
    fun `reports the error when every source failed`() = runTest {
        val result = ChainedCatalogProvider(
            listOf(Fake(CatalogResult.Error("timeout")), Fake(CatalogResult.Error("HTTP 500"))),
        ).lookupByBarcode("1")

        // Errors must not be reported as "no such product": the UI offers a retry for one
        // and immediate manual entry for the other.
        assertTrue(result is CatalogResult.Error)
    }

    @Test
    fun `reports not found only when every source answered cleanly`() = runTest {
        val result = ChainedCatalogProvider(
            listOf(Fake(CatalogResult.NotFound), Fake(CatalogResult.NotFound)),
        ).lookupByBarcode("1")

        assertEquals(CatalogResult.NotFound, result)
    }

    @Test
    fun `an error anywhere outweighs a clean miss elsewhere`() = runTest {
        val result = ChainedCatalogProvider(
            listOf(Fake(CatalogResult.Error("timeout")), Fake(CatalogResult.NotFound)),
        ).lookupByBarcode("1")

        // One source never answered, so "this product does not exist" would be a lie.
        assertTrue(result is CatalogResult.Error)
    }

    @Test
    fun `empty chain reports not found rather than crashing`() = runTest {
        assertEquals(CatalogResult.NotFound, ChainedCatalogProvider(emptyList()).lookupByBarcode("1"))
    }
}
