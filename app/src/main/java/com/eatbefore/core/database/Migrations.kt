package com.eatbefore.core.database

import androidx.room.migration.Migration

/**
 * Schema migrations. The array is currently empty because the app ships at schema
 * version 1. When bumping [EatBeforeDatabase.VERSION], add a [Migration] here rather
 * than relying on destructive fallback — user data must never be silently dropped.
 *
 * Exported schemas live in `app/schemas/` (see the `room.schemaLocation` KSP arg) and
 * are used by migration tests.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
