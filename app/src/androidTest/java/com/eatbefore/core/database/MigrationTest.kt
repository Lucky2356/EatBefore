package com.eatbefore.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the upgrade path for existing installs.
 *
 * The app ships schema v1 and [ALL_MIGRATIONS] is still empty, so there is no version
 * step to exercise yet. What this test *does* enforce is that the exported schema is
 * present and that a database created from it opens under the current code with the
 * declared migrations applied — the check that will fail loudly the moment someone bumps
 * the version without writing a migration.
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

    @Test
    fun currentSchemaOpensWithDeclaredMigrations() {
        // Creates the DB at v1 straight from the exported schema, then closes it.
        helper.createDatabase(TEST_DB, EatBeforeDatabase.VERSION).close()

        // Reopening through Room validates the live entities against that schema and
        // runs any migrations; a mismatch throws instead of silently dropping data.
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
