package com.example.snapstock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ClothingItem::class, TodoEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clothingItemDao(): ClothingItemDao
    abstract fun todoEntryDao(): TodoEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "snapstock_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `todo_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `itemIdsCsv` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }
    }
}

