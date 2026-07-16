package com.eatbefore.core.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eatbefore.core.database.EatBeforeDatabase
import com.eatbefore.core.database.entity.InventoryBatchEntity
import com.eatbefore.core.database.entity.ProductEntity
import com.eatbefore.core.database.entity.StorageLocationEntity
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.ProductSource
import com.eatbefore.domain.model.StorageType
import com.eatbefore.testutil.FakeAppClock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    private lateinit var db: EatBeforeDatabase
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, EatBeforeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = BackupManager(db, Json { ignoreUnknownKeys = true }, FakeAppClock())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        db.storageLocationDao().insert(
            StorageLocationEntity(
                id = 1,
                name = "Fridge",
                type = StorageType.FRIDGE,
                icon = null,
                sortOrder = 0,
                isDefault = true,
                isArchived = false,
            ),
        )
        db.productDao().insert(
            ProductEntity(
                id = 1, barcode = "4600266011152", barcodeType = BarcodeType.EAN_13,
                name = "Кефир", brand = "Домик", category = "Молочное", description = null,
                packageSize = "1 л", measurementUnit = MeasurementUnit.LITER, imageUri = null,
                source = ProductSource.USER, isUserCreated = true, createdAt = 1L, updatedAt = 1L,
            ),
        )
        db.inventoryBatchDao().insert(
            InventoryBatchEntity(
                id = 1, productId = 1, storageLocationId = 1, quantity = 1.0,
                initialQuantity = 1.0, measurementUnit = MeasurementUnit.LITER,
                purchaseDate = null, addedAt = 1L, expirationDate = 20_000L, openedAt = null,
                recommendedUseAfterOpeningDays = null, calculatedExpirationAfterOpening = null,
                status = BatchStatus.ACTIVE, note = null, price = null, currency = null,
                deletedAt = null, updatedAt = 1L,
            ),
        )
    }

    @Test
    fun exportThenImport_restoresEverything() = runTest {
        seed()
        val json = manager.export()

        // Wipe and restore.
        db.inventoryEventDao().deleteAll()
        db.inventoryBatchDao().deleteAll()
        db.productDao().deleteAll()
        db.storageLocationDao().deleteAll()
        assertTrue(db.productDao().getAll().isEmpty())

        val stats = manager.import(json)
        assertEquals(1, stats.products)
        assertEquals(1, stats.batches)

        val product = db.productDao().getById(1)!!
        assertEquals("Кефир", product.name)
        assertEquals("4600266011152", product.barcode)
        assertEquals(1, db.inventoryBatchDao().getAll().size)
        assertEquals(1, db.storageLocationDao().getAll().size)
    }

    @Test
    fun corruptJson_isRejectedAndKeepsData() = runTest {
        seed()
        try {
            manager.import("{not json at all")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // Data must be untouched.
            assertEquals(1, db.productDao().getAll().size)
        }
    }

    @Test
    fun countMismatch_failsIntegrityCheck() = runTest {
        seed()
        val json = manager.export()
        // Declare one more product than the file actually contains.
        val tampered = json.replaceFirst("\"products\":1", "\"products\":2")
        try {
            manager.import(tampered)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertEquals(1, db.productDao().getAll().size)
        }
    }

    @Test
    fun unsupportedVersion_isRejected() = runTest {
        seed()
        val json = manager.export().replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":99")
        try {
            manager.import(json)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }

    /** Merging a file exported from this very device must not duplicate the product. */
    @Test
    fun merge_reusesExistingProductByBarcode() = runTest {
        seed()
        val json = manager.export()

        manager.import(json, BackupManager.ImportMode.MERGE)

        val products = db.productDao().getAll()
        assertEquals(1, products.size)
        assertEquals("Кефир", products.first().name)
        // The batch has no stable identity yet, so it is appended — documented behaviour.
        assertEquals(2, db.inventoryBatchDao().getAll().size)
    }

    @Test
    fun merge_keepsLocalDataAndAddsNewProducts() = runTest {
        seed()
        val json = manager.export()

        // Local data the file knows nothing about must survive the merge.
        db.productDao().deleteAll()
        db.productDao().insert(
            ProductEntity(
                id = 0, barcode = "4601662000016", barcodeType = BarcodeType.EAN_13,
                name = "Молоко", brand = "Parmalat", category = null, description = null,
                packageSize = null, measurementUnit = MeasurementUnit.LITER, imageUri = null,
                source = ProductSource.USER, isUserCreated = true, createdAt = 2L, updatedAt = 2L,
            ),
        )

        manager.import(json, BackupManager.ImportMode.MERGE)

        val names = db.productDao().getAll().map { it.name }
        assertTrue(names.contains("Молоко"))
        assertTrue(names.contains("Кефир"))
    }

    /** Merged batches must point at the merged parents, not the file's original ids. */
    @Test
    fun merge_remapsBatchParents() = runTest {
        seed()
        val json = manager.export()
        manager.import(json, BackupManager.ImportMode.MERGE)

        val productIds = db.productDao().getAll().map { it.id }.toSet()
        val locationIds = db.storageLocationDao().getAll().map { it.id }.toSet()
        db.inventoryBatchDao().getAll().forEach { batch ->
            assertTrue(batch.productId in productIds)
            assertTrue(batch.storageLocationId in locationIds)
        }
    }
}
