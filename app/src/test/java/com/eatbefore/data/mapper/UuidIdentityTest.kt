package com.eatbefore.data.mapper

import com.eatbefore.domain.model.BarcodeType
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryBatch
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.model.ProductSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * A row's uuid is its identity on the other person's phone. It has to survive an ordinary
 * edit: if a write through the domain layer mints a new one, the peer sees the change as a
 * brand-new record and ends up with a duplicate instead of an update.
 *
 * This is not hypothetical — it shipped in v1.4.0. The domain models had no uuid, the
 * mappers could not carry one, and the entity default quietly assigned a fresh uuid on
 * every save. Two emulators exchanging journals produced two batches for one product.
 * The existing tests missed it because they build entities directly and never cross the
 * domain boundary, which is exactly where the identity was being dropped.
 */
class UuidIdentityTest {

    private val product = Product(
        id = 1,
        uuid = "product-uuid",
        barcode = "4620017700531",
        barcodeType = BarcodeType.EAN_13,
        name = "Молоко",
        source = ProductSource.USER,
        isUserCreated = true,
        createdAt = Instant.ofEpochMilli(1),
        updatedAt = Instant.ofEpochMilli(1),
    )

    private val batch = InventoryBatch(
        id = 1,
        uuid = "batch-uuid",
        productId = 1,
        storageLocationId = 1,
        quantity = 2.0,
        initialQuantity = 2.0,
        measurementUnit = MeasurementUnit.PIECE,
        addedAt = Instant.ofEpochMilli(1),
        status = BatchStatus.ACTIVE,
        updatedAt = Instant.ofEpochMilli(1),
    )

    private val event = InventoryEvent(
        id = 1,
        uuid = "event-uuid",
        inventoryBatchId = 1,
        productId = 1,
        eventType = EventType.ADDED,
        createdAt = Instant.ofEpochMilli(1),
    )

    @Test
    fun `product keeps its uuid across a round trip`() {
        assertEquals("product-uuid", product.toEntity().toDomain().uuid)
    }

    @Test
    fun `batch keeps its uuid across a round trip`() {
        assertEquals("batch-uuid", batch.toEntity().toDomain().uuid)
    }

    @Test
    fun `event keeps its uuid across a round trip`() {
        assertEquals("event-uuid", event.toEntity().toDomain().uuid)
    }

    /** The regression itself: using something up must not change what it is. */
    @Test
    fun `consuming a batch does not change its identity`() {
        val consumed = batch.copy(
            quantity = 0.0,
            status = BatchStatus.CONSUMED,
            deletedAt = Instant.ofEpochMilli(2),
            updatedAt = Instant.ofEpochMilli(2),
        )

        assertEquals(batch.toEntity().uuid, consumed.toEntity().uuid)
    }

    @Test
    fun `editing a product does not change its identity`() {
        val renamed = product.copy(name = "Молоко 3,2 %", updatedAt = Instant.ofEpochMilli(2))

        assertEquals(product.toEntity().uuid, renamed.toEntity().uuid)
    }

    /**
     * An event's author is stored the same way its identity is, and is just as easy to
     * drop: the domain model carried no device id at all until the history had to answer
     * "who threw out the sour cream?".
     */
    @Test
    fun `event keeps its author across a round trip`() {
        val fromPeer = event.copy(deviceId = "device-b")

        assertEquals("device-b", fromPeer.toEntity().toDomain().deviceId)
    }

    /** Our own actions stay unsigned, which is how the app tells them apart from a peer's. */
    @Test
    fun `an event written here carries no author`() {
        assertEquals("", event.toEntity().toDomain().deviceId)
    }

    /** Objects built in code before they are stored still get a usable identity. */
    @Test
    fun `a brand new object gets a generated uuid rather than a blank one`() {
        val fresh = batch.copy(id = 0, uuid = "").toEntity()

        assertTrue("uuid must not be blank", fresh.uuid.isNotBlank())
    }

    @Test
    fun `two brand new objects do not share an identity`() {
        val first = batch.copy(id = 0, uuid = "").toEntity()
        val second = batch.copy(id = 0, uuid = "").toEntity()

        assertTrue("generated uuids must differ", first.uuid != second.uuid)
    }
}
