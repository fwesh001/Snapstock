package com.example.snapstock.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClothingItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ClothingItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ClothingItem>): List<Long>

    @Update
    suspend fun updateItem(item: ClothingItem)

    @Delete
    suspend fun deleteItem(item: ClothingItem)

    @Query("SELECT * FROM clothing_items ORDER BY dateAdded DESC")
    fun getAllItems(): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items ORDER BY dateAdded DESC")
    suspend fun getAllItemsOnce(): List<ClothingItem>

    @Query("SELECT * FROM clothing_items ORDER BY dateAdded DESC LIMIT 1")
    fun getLastAddedItem(): Flow<ClothingItem?>

    @Query("SELECT * FROM clothing_items WHERE quantity <= 5 ORDER BY quantity ASC")
    fun getLowStockItems(): Flow<List<ClothingItem>>

    @Query(
        """
        SELECT * FROM clothing_items
        WHERE name LIKE '%' || :query || '%' COLLATE NOCASE
           OR category LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY dateAdded DESC
        """
    )
    fun searchItems(query: String): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE id = :itemId")
    suspend fun getItemById(itemId: Int): ClothingItem?
}

