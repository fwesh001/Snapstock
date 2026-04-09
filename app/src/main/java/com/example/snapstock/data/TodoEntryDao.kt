package com.example.snapstock.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodoEntry(todoEntry: TodoEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodoEntries(todoEntries: List<TodoEntry>): List<Long>

    @Query("SELECT * FROM todo_entries WHERE completed = 0 ORDER BY createdAt DESC")
    fun getPendingTodos(): Flow<List<TodoEntry>>

    @Query("SELECT * FROM todo_entries ORDER BY createdAt DESC")
    fun getAllTodos(): Flow<List<TodoEntry>>

    @Query("SELECT * FROM todo_entries ORDER BY createdAt DESC")
    suspend fun getAllTodosOnce(): List<TodoEntry>

    @Query("UPDATE todo_entries SET completed = 1 WHERE id = :todoId")
    suspend fun markCompleted(todoId: Int)

    @Query("DELETE FROM todo_entries WHERE id = :todoId")
    suspend fun deleteTodo(todoId: Int)

    @Query("DELETE FROM todo_entries WHERE completed = 1")
    suspend fun deleteCompletedTodos()
}