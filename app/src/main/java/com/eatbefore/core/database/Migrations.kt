package com.eatbefore.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds stable cross-device identifiers required by household sync (ADR-0004):
 * `uuid` on products, batches and events, plus `device_id` on events.
 *
 * Existing rows are backfilled with generated uuids. Local `id` values stay as they are —
 * they remain the primary key and every foreign key keeps pointing at them; the uuid is
 * the identity that survives travelling to another device.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_batches ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_events ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_events ADD COLUMN device_id TEXT NOT NULL DEFAULT ''")

        // A literal DEFAULT would give every row the same value, which a unique index
        // then rejects. hex(randomblob(16)) is evaluated per row, so each gets its own.
        listOf("products", "inventory_batches", "inventory_events").forEach { table ->
            db.execSQL("UPDATE $table SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''")
        }

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_products_uuid ON products(uuid)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_batches_uuid ON inventory_batches(uuid)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_events_uuid ON inventory_events(uuid)",
        )
    }
}

/**
 * Schema migrations. Every version bump gets one here rather than a destructive
 * fallback — user data must never be silently dropped.
 *
 * Exported schemas live in `app/schemas/` (see the `room.schemaLocation` KSP arg) and are
 * used by `MigrationTest`.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
