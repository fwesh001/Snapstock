package com.example.snapstock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val price: Double,
    val quantity: Int,
    val category: String,
    val imagePath: String,
    val patternHash: String?,
    val visualEmbedding: String? = null,
    val ocrText: String? = null,
    val ocrTokens: String? = null,
    val signatureVersion: Int = 1,
    val dateAdded: Long
)

