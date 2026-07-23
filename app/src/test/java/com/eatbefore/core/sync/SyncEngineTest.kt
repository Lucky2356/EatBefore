package com.eatbefore.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eatbefore.core.database.EatBeforeDatabase
import com.eatbefore.core.database.entity.InventoryBatchEntity
import com.eatbefore.core.database.entity.InventoryEventEntity
import com.eatbefore.core.database.entity.ProductEntity
import com.eatbefore.core.database.entity.StorageLocationEntity
import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.ProductSource
import com.eatbefore.domain.model.StorageType
import com.eatbefore.testutil.FakeAppClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Two households merging journals. These cover the rules from ADR-0004 that decide
 * whether the other person's phone can quietly destroy your data.
 */
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    private lateinit var db: EatBeforeDatabase
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EatBeforeDatabase::class.java,
        ).allowMainThreadQueries().build()
        engine = SyncEngine(db, FakeAppClock())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedLocal() {
        db.storageLocationDao().insert(
            StorageLocationEntity(
                id = 1,
                name = "Холодильник",
                type = StorageType.FRIDGE,
                icon = null,
                sortOrder = 0,
                isDefault = true,
                isArchived = false,
            ),
        )
    }

    private fun peerJournal(
        batchQuantity: Double = 2.0,
        batchUpdatedAt: Long = 100L,
        batchStatus: String = "ACTIVE",
        events: List<SyncEvent> = emptyList(),
    ) = SyncJournal(
        deviceId = "peer-device",
        writtenAtEpochMillis = 100L,
        locations = listOf(SyncLocation(name = "Холодильник", type = "FRIDGE")),
        products = listOf(
            SyncProduct(
                uuid = "p-1", barcode = "4620017700531", barcodeType = "EAN_13",
                name = "Tea nic лимон", brand = null, category = null, packageSize = null,
                measurementUnit = "PIECE", imageUri = null, updatedAt = 100L,
            ),
        ),
        batches = listOf(
            SyncBatch(
                uuid = "b-1", productUuid = "p-1", locationName = "Холодильник",
                quantity = batchQuantity, initialQuantity = 2.0, measurementUnit = "PIECE",
                addedAt = 100L, expirationDate = null, openedAt = null, status = batchStatus,
                note = null, deletedAt = null, updatedAt = batchUpdatedAt,
            ),
        ),
        events = events,
    )

    @Test
    fun `merge brings in a product and batch the device has never seen`() = runTest {
        seedLocal()

        val stats = engine.merge(peerJournal())

        assertEquals(1, stats.productsAdded)
        assertEquals(1, stats.batchesAdded)
        val product = db.productDao().getByUuid("p-1")
        assertNotNull(product)
        assertEquals("Tea nic лимон", product!!.name)
        assertEquals(2.0, db.inventoryBatchDao().getByUuid("b-1")!!.quantity, 0.001)
    }

    /** Syncing twice must not duplicate anything — the operation has to be retryable. */
    @Test
    fun `merging the same journal twice changes nothing the second time`() = runTest {
        seedLocal()
        val journal = peerJournal(
            events = listOf(
                SyncEvent(
                    uuid = "e-1", batchUuid = "b-1", productUuid = "p-1", eventType = "ADDED",
                    oldQuantity = null, newQuantity = 2.0, reason = null, createdAt = 100L,
                    deviceId = "peer-device",
                ),
            ),
        )

        engine.merge(journal)
        val second = engine.merge(journal)

        assertEquals(0, second.productsAdded)
        assertEquals(0, second.batchesAdded)
        assertEquals(0, second.eventsAdded)
        assertEquals(1, db.productDao().getAll().size)
        assertEquals(1, db.inventoryBatchDao().getAll().size)
        assertEquals(1, db.inventoryEventDao().getAll().size)
    }

    @Test
    fun `a newer peer change wins over the local copy`() = runTest {
        seedLocal()
        engine.merge(peerJournal(batchQuantity = 2.0, batchUpdatedAt = 100L))

        // The other person used one up later than our copy was touched.
        val stats = engine.merge(peerJournal(batchQuantity = 1.0, batchUpdatedAt = 200L))

        assertEquals(1, stats.batchesUpdated)
        assertEquals(1.0, db.inventoryBatchDao().getByUuid("b-1")!!.quantity, 0.001)
    }

    /** The important direction: a stale peer must not undo a newer local change. */
    @Test
    fun `an older peer change does not overwrite a newer local one`() = runTest {
        seedLocal()
        engine.merge(peerJournal(batchQuantity = 2.0, batchUpdatedAt = 300L))

        val stats = engine.merge(peerJournal(batchQuantity = 9.0, batchUpdatedAt = 100L))

        assertEquals(0, stats.batchesUpdated)
        assertEquals(2.0, db.inventoryBatchDao().getByUuid("b-1")!!.quantity, 0.001)
    }

    /** Someone finishing a product elsewhere must not reappear as available here. */
    @Test
    fun `a batch used up on the other phone becomes used up here`() = runTest {
        seedLocal()
        engine.merge(peerJournal(batchUpdatedAt = 100L))

        engine.merge(peerJournal(batchQuantity = 0.0, batchUpdatedAt = 200L, batchStatus = "CONSUMED"))

        assertEquals(BatchStatus.CONSUMED, db.inventoryBatchDao().getByUuid("b-1")!!.status)
    }

    @Test
    fun `both phones calling the fridge the same name share one location`() = runTest {
        seedLocal()

        engine.merge(peerJournal())

        // "Холодильник" already existed locally; a second one would split the shelf in two.
        assertEquals(1, db.storageLocationDao().getAll().count { it.name == "Холодильник" })
    }

    @Test
    fun `an unknown location from a peer is created rather than dropped`() = runTest {
        seedLocal()
        val journal = peerJournal().copy(
            locations = listOf(SyncLocation(name = "Погреб", type = "PANTRY")),
            batches = peerJournal().batches.map { it.copy(locationName = "Погреб") },
        )

        engine.merge(journal)

        val created = db.storageLocationDao().getAll().firstOrNull { it.name == "Погреб" }
        assertNotNull(created)
        // Only this device decides its own default place.
        assertEquals(false, created!!.isDefault)
    }

    @Test
    fun `an event whose batch is missing is skipped instead of failing the merge`() = runTest {
        seedLocal()
        val journal = peerJournal(
            events = listOf(
                SyncEvent(
                    uuid = "e-orphan", batchUuid = "b-does-not-exist", productUuid = "p-1",
                    eventType = "CONSUMED", oldQuantity = 1.0, newQuantity = 0.0, reason = null,
                    createdAt = 100L, deviceId = "peer-device",
                ),
            ),
        )

        val stats = engine.merge(journal)

        assertEquals(0, stats.eventsAdded)
        // The rest of the journal still landed.
        assertEquals(1, stats.batchesAdded)
    }

    @Test
    fun `an unknown enum from a newer peer falls back instead of crashing`() = runTest {
        seedLocal()
        val journal = peerJournal().copy(
            batches = peerJournal().batches.map { it.copy(status = "SOMETHING_NEW") },
        )

        engine.merge(journal)

        assertEquals(BatchStatus.ACTIVE, db.inventoryBatchDao().getByUuid("b-1")!!.status)
    }

    @Test
    fun `own journal carries this device's id on locally written events`() = runTest {
        seedLocal()
        val productId = db.productDao().insert(
            ProductEntity(
                barcode = null, barcodeType = BarcodeType.NONE, name = "Молоко", brand = null,
                category = null, description = null, packageSize = null,
                measurementUnit = MeasurementUnit.LITER, imageUri = null,
                source = ProductSource.USER, isUserCreated = true, createdAt = 1L, updatedAt = 1L,
            ),
        )
        val batchId = db.inventoryBatchDao().insert(
            InventoryBatchEntity(
                productId = productId, storageLocationId = 1, quantity = 1.0,
                initialQuantity = 1.0, measurementUnit = MeasurementUnit.LITER,
                purchaseDate = null, addedAt = 1L, expirationDate = null, openedAt = null,
                recommendedUseAfterOpeningDays = null, calculatedExpirationAfterOpening = null,
                status = BatchStatus.ACTIVE, note = null, price = null, currency = null,
                deletedAt = null, updatedAt = 1L,
            ),
        )
        db.inventoryEventDao().insert(
            InventoryEventEntity(
                inventoryBatchId = batchId, productId = productId, eventType = EventType.ADDED,
                oldQuantity = null, newQuantity = 1.0, previousStorageLocationId = null,
                newStorageLocationId = 1, reason = null, createdAt = 1L, metadata = null,
            ),
        )

        val journal = engine.buildOwnJournal("my-device")

        assertEquals("my-device", journal.deviceId)
        assertEquals(1, journal.events.size)
        // Locally written events store an empty id and are stamped on publish.
        assertEquals("my-device", journal.events.first().deviceId)
    }

    /** A round trip through the journal must not change what the data means. */
    @Test
    fun `a peer merging our journal ends up with the same batch`() = runTest {
        seedLocal()
        engine.merge(peerJournal(batchQuantity = 3.0, batchUpdatedAt = 500L))

        val republished = engine.buildOwnJournal("my-device")
        val batch = republished.batches.first { it.uuid == "b-1" }

        assertEquals(3.0, batch.quantity, 0.001)
        assertEquals("Холодильник", batch.locationName)
        assertEquals("p-1", batch.productUuid)
    }

    /**
     * The end-to-end promise: two phones exchanging journals both ways end up agreeing.
     * Everything else in this class checks one rule; this checks that the rules compose.
     */
    @Test
    fun `two devices exchanging journals converge on the same stock`() = runTest {
        val phoneB = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EatBeforeDatabase::class.java,
        ).allowMainThreadQueries().build()
        val engineB = SyncEngine(phoneB, FakeAppClock())

        try {
            // Each phone starts with its own fridge and its own product.
            seedLocal()
            phoneB.storageLocationDao().insert(
                StorageLocationEntity(
                    id = 1,
                    name = "Холодильник",
                    type = StorageType.FRIDGE,
                    icon = null,
                    sortOrder = 0,
                    isDefault = true,
                    isArchived = false,
                ),
            )
            addProductWithBatch(db, "Молоко", quantity = 1.0)
            addProductWithBatch(phoneB, "Хлеб", quantity = 2.0)

            // A publishes, B merges; then B publishes and A merges.
            engineB.merge(engine.buildOwnJournal("phone-a"))
            engine.merge(engineB.buildOwnJournal("phone-b"))

            val namesOnA = db.productDao().getAll().map { it.name }.sorted()
            val namesOnB = phoneB.productDao().getAll().map { it.name }.sorted()
            assertEquals(listOf("Молоко", "Хлеб"), namesOnA)
            assertEquals(namesOnA, namesOnB)
            assertEquals(2, db.inventoryBatchDao().getAll().size)
            assertEquals(2, phoneB.inventoryBatchDao().getAll().size)

            // A second round must be a no-op rather than doubling everything.
            engineB.merge(engine.buildOwnJournal("phone-a"))
            engine.merge(engineB.buildOwnJournal("phone-b"))
            assertEquals(2, db.inventoryBatchDao().getAll().size)
            assertEquals(2, phoneB.inventoryBatchDao().getAll().size)
            // One fridge, not two.
            assertEquals(1, phoneB.storageLocationDao().getAll().size)
        } finally {
            phoneB.close()
        }
    }

    private suspend fun addProductWithBatch(target: EatBeforeDatabase, name: String, quantity: Double) {
        val productId = target.productDao().insert(
            ProductEntity(
                barcode = null, barcodeType = BarcodeType.NONE, name = name, brand = null,
                category = null, description = null, packageSize = null,
                measurementUnit = MeasurementUnit.PIECE, imageUri = null,
                source = ProductSource.USER, isUserCreated = true, createdAt = 1L, updatedAt = 1L,
            ),
        )
        target.inventoryBatchDao().insert(
            InventoryBatchEntity(
                productId = productId, storageLocationId = 1, quantity = quantity,
                initialQuantity = quantity, measurementUnit = MeasurementUnit.PIECE,
                purchaseDate = null, addedAt = 1L, expirationDate = null, openedAt = null,
                recommendedUseAfterOpeningDays = null, calculatedExpirationAfterOpening = null,
                status = BatchStatus.ACTIVE, note = null, price = null, currency = null,
                deletedAt = null, updatedAt = 1L,
            ),
        )
    }
}
