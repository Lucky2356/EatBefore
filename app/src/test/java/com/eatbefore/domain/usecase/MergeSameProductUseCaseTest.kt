package com.eatbefore.domain.usecase

import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.ProductSource
import com.eatbefore.testutil.FakeProductRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MergeSameProductUseCaseTest {

    private lateinit var products: FakeProductRepository
    private lateinit var useCase: MergeSameProductUseCase

    @Before
    fun setUp() {
        products = FakeProductRepository()
        useCase = MergeSameProductUseCase(products)
    }

    @Test
    fun matchesByBarcodeFirst() = runTest {
        val id = products.upsert(
            Product(
                barcode = "4600000000017",
                barcodeType = BarcodeType.EAN_13,
                name = "Water",
                source = ProductSource.SCAN_CACHE,
                isUserCreated = false,
            ),
        )
        val found = useCase.findDuplicate(name = "Anything", brand = null, barcode = "4600000000017")
        assertEquals(id, found?.id)
    }

    @Test
    fun matchesUserProductByNameAndBrandCaseInsensitive() = runTest {
        val id = products.upsert(Product(name = "Butter", brand = "Farm"))
        val found = useCase.findDuplicate(name = "  butter ", brand = "FARM")
        assertEquals(id, found?.id)
    }

    @Test
    fun noMatch_returnsNull() = runTest {
        products.upsert(Product(name = "Butter", brand = "Farm"))
        assertNull(useCase.findDuplicate(name = "Cheese", brand = "Farm"))
    }
}
