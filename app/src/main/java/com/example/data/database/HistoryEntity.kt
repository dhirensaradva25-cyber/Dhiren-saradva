package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "caption" or "hashtag"
    val prompt: String,
    val result: String,
    val platform: String, // "instagram", "youtube", "facebook", or "all"
    val timestamp: Long = System.currentTimeMillis()
)
