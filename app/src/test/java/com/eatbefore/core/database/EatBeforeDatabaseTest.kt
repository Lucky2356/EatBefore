package com.eatbefore.core.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.eatbefore.core.database.entity.InventoryBatchEntity
import com.eatbefore.core.database.entity.ProductEntity
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.ProductSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class EatBeforeDatabaseTest {

    private lateinit var db: EatBeforeDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, EatBeforeDatabase::class.java)
            .allowMainThreadQueries()
            // Re-run the production seed so tests exercise the real onCreate callback.
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    DefaultStorageLocations.entities.forEach { loc ->
                        database.execSQL(
                            "INSERT INTO storage_locations " +
                                "(name, type, icon, sort_order, is_default, is_archived) " +
                                "VALUES (?, ?, ?, ?, ?, ?)",
                            arrayOf<Any?>(
                                loc.name,
                                loc.type.name,
                                loc.icon,
                                loc.sortOrder,
                                if (loc.isDefault) 1 else 0,
                                if (loc.isArchived) 1 else 0,
                            ),
                        )
                    }
                }
            })
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedsDefaultStorageLocations() = runTest {
        val locations = db.storageLocationDao().observeActive().first()
        assertEquals(4, locations.size)
        val default = db.storageLocationDao().getDefault()
        assertNotNull(default)
        assertEquals("Fridge", default!!.name)
    }

    @Test
    fun insertProductAndBatch_appearsInPresentStock() = runTest {
        val productId = db.productDao().insert(sampleProduct())
        val locationId = db.storageLocationDao().getDefault()!!.id
        db.inventoryBatchDao().insert(sampleBatch(productId, locationId))

        val present = db.inventoryBatchDao().observePresentByExpiry().first()
        assertEquals(1, present.size)
        assertEquals("Milk", present.first().product.name)
        assertEquals(1, db.inventoryBatchDao().observePresentCount().first())
    }

    @Test
    fun softDeletedBatch_isExcludedFromPresentStock() = runTest {
        val productId = db.productDao().insert(sampleProduct())
        val locationId = db.storageLocationDao().getDefault()!!.id
        val batchId = db.inventoryBatchDao().insert(sampleBatch(productId, locationId))

        val batch = db.inventoryBatchDao().getById(batchId)!!
        db.inventoryBatchDao().update(
            batch.copy(status = BatchStatus.DISCARDED, deletedAt = 123L),
        )

        assertTrue(db.inventoryBatchDao().observePresentByExpiry().first().isEmpty())
        // The row still exists physically (history preserved), just not "present".
        assertNotNull(db.inventoryBatchDao().getById(batchId))
    }

    private fun sampleProduct() = ProductEntity(
        barcode = null,
        barcodeType = BarcodeType.NONE,
        name = "Milk",
        brand = "Farm",
        category = "Dairy",
        description = null,
        packageSize = "1 L",
        measurementUnit = MeasurementUnit.LITER,
        imageUri = null,
        source = ProductSource.USER,
        isUserCreated = true,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun sampleBatch(productId: Long, locationId: Long) = InventoryBatchEntity(
        productId = productId,
        storageLocationId = locationId,
        quantity = 1.0,
        initialQuantity = 1.0,
        measurementUnit = MeasurementUnit.LITER,
        purchaseDate = null,
        addedAt = 0L,
        expirationDate = 20_000L,
        openedAt = null,
        recommendedUseAfterOpeningDays = null,
        calculatedExpirationAfterOpening = null,
        status = BatchStatus.ACTIVE,
        note = null,
        price = null,
        currency = null,
        deletedAt = null,
        updatedAt = 0L,
    )
}
