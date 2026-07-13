package com.eatbefore.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eatbefore.core.database.ALL_MIGRATIONS
import com.eatbefore.core.database.DefaultStorageLocations
import com.eatbefore.core.database.EatBeforeDatabase
import com.eatbefore.core.database.dao.InventoryBatchDao
import com.eatbefore.core.database.dao.InventoryEventDao
import com.eatbefore.core.database.dao.ProductDao
import com.eatbefore.core.database.dao.ShoppingListDao
import com.eatbefore.core.database.dao.StorageLocationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): EatBeforeDatabase {
        return Room.databaseBuilder(
            context,
            EatBeforeDatabase::class.java,
            EatBeforeDatabase.NAME,
        )
            .addMigrations(*ALL_MIGRATIONS)
            .addCallback(SeedCallback)
            .build()
    }

    /**
     * Seeds preset storage locations on first creation using raw inserts, avoiding a DAO
     * dependency during database construction. Runs inside Room's creation transaction.
     */
    private object SeedCallback : androidx.room.RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            DefaultStorageLocations.entities.forEach { loc ->
                db.execSQL(
                    "INSERT INTO storage_locations " +
                        "(name, type, icon, sort_order, is_default, is_archived) " +
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
    }

    @Provides fun provideProductDao(db: EatBeforeDatabase): ProductDao = db.productDao()

    @Provides
    fun provideStorageLocationDao(db: EatBeforeDatabase): StorageLocationDao =
        db.storageLocationDao()

    @Provides
    fun provideInventoryBatchDao(db: EatBeforeDatabase): InventoryBatchDao =
        db.inventoryBatchDao()

    @Provides
    fun provideInventoryEventDao(db: EatBeforeDatabase): InventoryEventDao =
        db.inventoryEventDao()

    @Provides
    fun provideShoppingListDao(db: EatBeforeDatabase): ShoppingListDao = db.shoppingListDao()
}
