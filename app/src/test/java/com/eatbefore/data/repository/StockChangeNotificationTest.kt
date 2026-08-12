package com.eatbefore.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eatbefore.core.database.EatBeforeDatabase
import com.eatbefore.core.database.entity.ProductEntity
import com.eatbefore.core.database.entity.StorageLocationEntity
import com.eatbefore.core.widget.StockChangeNotifier
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.ProductSource
import com.eatbefore.domain.model.StorageType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every stock change has to reach the home-screen widget.
 *
 * The widget reads the database only when the system refreshes it — every half an hour by
 * its own configuration — so before this hook, writing off the milk left the widget
 * claiming for up to thirty minutes that it expires today. That is the one surface the
 * user reads *without* opening the app, which makes a stale one worse than none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class StockChangeNotificationTest {

    private lateinit var db: EatBeforeDatabase
    private lateinit var repository: InventoryRepositoryImpl
    private var notifications = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, EatBeforeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = InventoryRepositoryImpl(
            db,
            db.inventoryBatchDao(),
            db.inventoryEventDao(),
            StockChangeNotifier { notifications++ },
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedProduct(): Long = db.productDao().insert(
        ProductEntity(
            barcode = null,
            barcodeType = BarcodeType.NONE,
            name = "Молоко",
            brand = null,
            category = null,
            description = null,
            packageSize = null,
            measurementUnit = MeasurementUnit.LITER,
            imageUri = null,
            source = ProductSource.USER,
            isUserCreated = true,
            createdAt = 0L,
            updatedAt = 0L,
        ),
    )

    private suspend fun locationId(): Long = db.storageLocationDao().insert(
        StorageLocationEntity(
            name = "Fridge",
            type = StorageType.FRIDGE,
            icon = null,
            sortOrder = 0,
            isDefault = true,
            isArchived = false,
        ),
    )

    private fun batch(productId: Long, locationId: Long) = InventoryBatch(
        productId = productId,
        storageLocationId = locationId,
        quantity = 1.0,
        initialQuantity = 1.0,
        measurementUnit = MeasurementUnit.LITER,
    )

    private fun event(batchId: Long, productId: Long) = InventoryEvent(
        inventoryBatchId = batchId,
        productId = productId,
        eventType = EventType.ADDED,
    )

    @Test
    fun `adding a batch tells the widget`() = runTest {
        val productId = seedProduct()
        val locationId = locationId()

        repository.addBatchWithEvent(batch(productId, locationId)) { id -> event(id, productId) }

        assertEquals(1, notifications)
    }

    @Test
    fun `changing a batch tells the widget`() = runTest {
        val productId = seedProduct()
        val locationId = locationId()
        val batchId = repository.addBatchWithEvent(batch(productId, locationId)) { id ->
            event(id, productId)
        }
        val stored = repository.getBatch(batchId)!!

        repository.updateBatchWithEvent(stored.copy(quantity = 0.0), event(batchId, productId))

        assertEquals("both the add and the change must be announced", 2, notifications)
    }

    /**
     * The announcement happens after the transaction commits. A widget redraw reads the
     * database, and reading it from inside the write would see the old rows at best.
     */
    @Test
    fun `the new state is already readable when the widget is told`() = runTest {
        val productId = seedProduct()
        val locationId = locationId()
        var visibleAtNotification = -1
        val repo = InventoryRepositoryImpl(
            db,
            db.inventoryBatchDao(),
            db.inventoryEventDao(),
            StockChangeNotifier { visibleAtNotification = db.inventoryBatchDao().getAll().size },
        )

        repo.addBatchWithEvent(batch(productId, locationId)) { id -> event(id, productId) }

        assertEquals(1, visibleAtNotification)
    }
}
