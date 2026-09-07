package com.eatbefore.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Schema versions, named so a migration reads as a step between two of them rather than
// as a pair of bare numbers.
private const val VERSION_1 = 1
private const val VERSION_2 = 2
private const val VERSION_3 = 3
private const val VERSION_4 = 4

/**
 * Adds stable cross-device identifiers required by household sync (ADR-0004):
 * `uuid` on products, batches and events, plus `device_id` on events.
 *
 * Existing rows are backfilled with generated uuids. Local `id` values stay as they are —
 * they remain the primary key and every foreign key keeps pointing at them; the uuid is
 * the identity that survives travelling to another device.
 */
val MIGRATION_1_2 = object : Migration(VERSION_1, VERSION_2) {
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
 * Adds `deleted_at` to products, so a product card can be struck off the catalogue.
 *
 * A mark rather than a `DELETE`: batches and events keep pointing at the card, and the
 * other phone has to be told about the deletion — a row that simply disappeared looks to
 * a peer like a row it has and we don't, and the next exchange would hand it back.
 *
 * Existing rows get NULL, which is exactly right: nothing has been deleted yet.
 */
val MIGRATION_2_3 = object : Migration(VERSION_2, VERSION_3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN deleted_at INTEGER DEFAULT NULL")
    }
}

/**
 * Adds `notifications_muted` to products, so the daily reminder can be silenced for one
 * product without silencing it for everything.
 *
 * The default is 0 — «keep reminding» — and that is the whole point of stating it: an
 * upgrade must not quietly stop warning anyone about food already in the fridge. NOT NULL
 * with a default is safe here because SQLite fills existing rows with it in place.
 */
val MIGRATION_3_4 = object : Migration(VERSION_3, VERSION_4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE products ADD COLUMN notifications_muted INTEGER NOT NULL DEFAULT 0",
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
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
