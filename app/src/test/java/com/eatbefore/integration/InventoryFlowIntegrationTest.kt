package com.eatbefore.integration

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.eatbefore.core.database.DefaultStorageLocations
import com.eatbefore.core.database.EatBeforeDatabase
import com.eatbefore.data.repository.HistoryRepositoryImpl
import com.eatbefore.data.repository.InventoryRepositoryImpl
import com.eatbefore.data.repository.ProductRepositoryImpl
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.usecase.AddManualProductUseCase
import com.eatbefore.domain.usecase.ChangeQuantityUseCase
import com.eatbefore.domain.usecase.MergeSameProductUseCase
import com.eatbefore.domain.usecase.UndoLastActionUseCase
import com.eatbefore.testutil.FakeAppClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real stack — Room + repository implementations + use cases — end to end,
 * verifying that a manual add persists, appears in present stock, records history, and
 * that undo reverses a quantity change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InventoryFlowIntegrationTest {

    private lateinit var db: EatBeforeDatabase
    private lateinit var addManual: AddManualProductUseCase
    private lateinit var changeQuantity: ChangeQuantityUseCase
    private lateinit var undo: UndoLastActionUseCase
    private lateinit var inventory: InventoryRepositoryImpl
    private val clock = FakeAppClock()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, EatBeforeDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    DefaultStorageLocations.entities.forEach { loc ->
                        database.execSQL(
                            "INSERT INTO storage_locations (name, type, icon, sort_order, is_default, is_archived) " +
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

        val products = ProductRepositoryImpl(db.productDao())
        inventory = InventoryRepositoryImpl(db, db.inventoryBatchDao(), db.inventoryEventDao())
        val history = HistoryRepositoryImpl(db.inventoryEventDao())
        val merge = MergeSameProductUseCase(products)
        addManual = AddManualProductUseCase(products, inventory, merge, clock)
        changeQuantity = ChangeQuantityUseCase(inventory, clock)
        undo = UndoLastActionUseCase(history, inventory, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun manualAdd_persistsAndRecordsHistory() = runTest {
        val locationId = db.storageLocationDao().getDefault()!!.id
        addManual(
            AddManualProductUseCase.Params(
                name = "Kefir",
                brand = "Farm",
                storageLocationId = locationId,
                quantity = 2.0,
            ),
        )

        val present = inventory.observePresentByExpiry().first()
        assertEquals(1, present.size)
        assertEquals("Kefir", present.first().product.name)

        val events = db.inventoryEventDao().observeAll().first()
        assertEquals(1, events.size)
        assertEquals(EventType.ADDED, events.first().eventType)
    }

    @Test
    fun changeQuantityThenUndo_restoresOriginal() = runTest {
        val locationId = db.storageLocationDao().getDefault()!!.id
        val batchId = addManual(
            AddManualProductUseCase.Params(name = "Rice", storageLocationId = locationId, quantity = 5.0),
        )
        changeQuantity(batchId, 2.0)
        assertEquals(2.0, inventory.getBatch(batchId)!!.quantity, 0.0)

        assertTrue(undo())
        assertEquals(5.0, inventory.getBatch(batchId)!!.quantity, 0.0)
    }
}
