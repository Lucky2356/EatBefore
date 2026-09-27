package com.eatbefore.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eatbefore.core.database.dao.findHomemadeProductByName
import com.eatbefore.core.database.dao.findUserProductByNameAndBrand
import com.eatbefore.core.database.entity.ProductEntity
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.HomemadeKind
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.ProductSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * "Is this the same product typed again" against a real SQLite. The lookups used to compare
 * names with SQL LOWER(), which SQLite only guarantees for ASCII — so a Russian name typed
 * twice with a different capital letter quietly became two cards, and a fake repository
 * comparing in Kotlin could never have caught it.
 */
@RunWith(RobolectricTestRunner::class)
class ProductLookupsTest {

    private lateinit var db: EatBeforeDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EatBeforeDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insert(name: String, brand: String? = null, kind: HomemadeKind? = null): Long =
        db.productDao().insert(
            ProductEntity(
                barcode = null, barcodeType = BarcodeType.NONE, name = name, brand = brand,
                category = null, description = null, packageSize = null,
                measurementUnit = MeasurementUnit.PIECE, imageUri = null, source = ProductSource.USER,
                isUserCreated = true, createdAt = 1L, updatedAt = 1L, homemadeKind = kind,
            ),
        )

    @Test
    fun aCyrillicNameMatchesWhateverItsCapitals() = runTest {
        val id = insert("Огурцы Маринованные", brand = "Дядя Ваня")

        val found = db.productDao().findUserProductByNameAndBrand("Огурцы маринованные", "дядя ваня")

        assertEquals(id, found?.id)
    }

    @Test
    fun aDifferentBrandIsADifferentProduct() = runTest {
        insert("Кефир", brand = "Домик")

        assertNull(db.productDao().findUserProductByNameAndBrand("Кефир", "Простоквашино"))
        assertNull(db.productDao().findUserProductByNameAndBrand("Кефир", null))
    }

    @Test
    fun homemadeAndBoughtOfOneNameStayApart() = runTest {
        val bought = insert("Варенье вишнёвое")
        val jar = insert("Варенье Вишнёвое", kind = HomemadeKind.PRESERVE)

        assertEquals(bought, db.productDao().findUserProductByNameAndBrand("варенье вишнёвое", null)?.id)
        assertEquals(jar, db.productDao().findHomemadeProductByName("Варенье вишнёвое", HomemadeKind.PRESERVE)?.id)
        assertNull(db.productDao().findHomemadeProductByName("Варенье вишнёвое", HomemadeKind.DISH))
    }
}
