package com.eatbefore.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the upgrade path for existing installs: a user upgrading from the store must
 * keep their data, and a mismatch has to fail loudly here rather than on their phone.
 *
 * When adding a migration:
 * 1. bump [EatBeforeDatabase.VERSION] and let KSP export `app/schemas/<version>.json`;
 * 2. add the `Migration(n, n+1)` to [ALL_MIGRATIONS];
 * 3. add a test here that runs it via [helper] and asserts the data survives.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        EatBeforeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /** The v1→v2 step adds uuid/device_id. Existing rows must survive and get uuids. */
    @Test
    fun migrate1To2_keepsDataAndBackfillsUniqueUuids() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO storage_locations (id, name, type, icon, sort_order, is_default, is_archived)
                VALUES (1, 'Холодильник', 'FRIDGE', NULL, 0, 1, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO products (id, barcode, barcode_type, name, brand, category, description,
                    package_size, measurement_unit, image_uri, source, is_user_created, created_at, updated_at)
                VALUES (1, '4620017700531', 'EAN_13', 'Tea nic лимон', NULL, NULL, NULL,
                    NULL, 'PIECE', NULL, 'SCAN_CACHE', 0, 1, 1),
                    (2, NULL, 'NONE', 'Молоко', 'Простоквашино', NULL, NULL,
                    NULL, 'LITER', NULL, 'USER', 1, 1, 1)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO inventory_batches (id, product_id, storage_location_id, quantity,
                    initial_quantity, measurement_unit, purchase_date, added_at, expiration_date,
                    opened_at, recommended_use_after_opening_days, calculated_expiration_after_opening,
                    status, note, price, currency, deleted_at, updated_at)
                VALUES (1, 1, 1, 1.0, 1.0, 'PIECE', NULL, 1, NULL, NULL, NULL, NULL,
                    'ACTIVE', NULL, NULL, NULL, NULL, 1)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO inventory_events (id, inventory_batch_id, product_id, event_type,
                    old_quantity, new_quantity, previous_storage_location_id,
                    new_storage_location_id, reason, created_at, metadata)
                VALUES (1, 1, 1, 'ADDED', NULL, 1.0, NULL, 1, NULL, 1, NULL),
                    (2, 1, 1, 'OPENED', NULL, NULL, NULL, NULL, NULL, 2, NULL)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT id, name, uuid FROM products ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("Tea nic лимон", c.getString(1))
            val first = c.getString(2)
            c.moveToNext()
            val second = c.getString(2)
            assertTrue("uuid must be backfilled", first.isNotBlank() && second.isNotBlank())
            // A literal DEFAULT would hand every row the same value and break the index.
            assertNotEquals("uuids must differ per row", first, second)
        }

        db.query("SELECT uuid, device_id FROM inventory_events ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertTrue(c.getString(0).isNotBlank())
            // Events recorded before sync existed belong to no known device.
            assertEquals("", c.getString(1))
        }

        db.query("SELECT COUNT(*) FROM inventory_batches").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.close()
    }

    /** The unique index must actually be in force after the migration. */
    @Test
    fun migrate1To2_rejectsDuplicateUuids() {
        helper.createDatabase(TEST_DB, 1).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.execSQL(
            """
            INSERT INTO products (id, uuid, barcode, barcode_type, name, brand, category, description,
                package_size, measurement_unit, image_uri, source, is_user_created, created_at, updated_at)
            VALUES (1, 'fixed-uuid', NULL, 'NONE', 'A', NULL, NULL, NULL, NULL, 'PIECE', NULL, 'USER', 1, 1, 1)
            """.trimIndent(),
        )
        val duplicate = runCatching {
            db.execSQL(
                """
                INSERT INTO products (id, uuid, barcode, barcode_type, name, brand, category, description,
                    package_size, measurement_unit, image_uri, source, is_user_created, created_at, updated_at)
                VALUES (2, 'fixed-uuid', NULL, 'NONE', 'B', NULL, NULL, NULL, NULL, 'PIECE', NULL, 'USER', 1, 1, 1)
                """.trimIndent(),
            )
        }
        assertTrue("duplicate uuid must be rejected", duplicate.isFailure)
        db.close()
    }

    @Test
    fun currentSchemaOpensWithDeclaredMigrations() {
        helper.createDatabase(TEST_DB, EatBeforeDatabase.VERSION).close()

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            EatBeforeDatabase::class.java,
            TEST_DB,
        )
            .addMigrations(*ALL_MIGRATIONS)
            .build()

        db.openHelper.writableDatabase.use { open ->
            assertTrue(open.isOpen)
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
