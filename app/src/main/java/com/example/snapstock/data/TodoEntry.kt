package com.example.snapstock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_entries")
data class TodoEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val itemIdsCsv: String,
    val createdAt: Long,
    val completed: Boolean = false
)