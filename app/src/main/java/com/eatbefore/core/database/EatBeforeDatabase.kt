package com.eatbefore.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.eatbefore.core.database.converter.Converters
import com.eatbefore.core.database.dao.InventoryBatchDao
import com.eatbefore.core.database.dao.InventoryEventDao
import com.eatbefore.core.database.dao.ProductDao
import com.eatbefore.core.database.dao.ShoppingListDao
import com.eatbefore.core.database.dao.StorageLocationDao
import com.eatbefore.core.database.entity.InventoryBatchEntity
import com.eatbefore.core.database.entity.InventoryEventEntity
import com.eatbefore.core.database.entity.ProductEntity
import com.eatbefore.core.database.entity.ShoppingListItemEntity
import com.eatbefore.core.database.entity.StorageLocationEntity

@Database(
    entities = [
        ProductEntity::class,
        StorageLocationEntity::class,
        InventoryBatchEntity::class,
        InventoryEventEntity::class,
        ShoppingListItemEntity::class,
    ],
    version = EatBeforeDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class EatBeforeDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun storageLocationDao(): StorageLocationDao
    abstract fun inventoryBatchDao(): InventoryBatchDao
    abstract fun inventoryEventDao(): InventoryEventDao
    abstract fun shoppingListDao(): ShoppingListDao

    companion object {
        /** v2 added uuid/device_id for household sync (ADR-0004). */
        const val VERSION = 2
        const val NAME = "eatbefore.db"
    }
}
