package com.example.data.repository

import com.example.data.database.HistoryDao
import com.example.data.database.HistoryEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    suspend fun insert(history: HistoryEntity) {
        historyDao.insertHistory(history)
    }

    suspend fun deleteById(id: Int) {
        historyDao.deleteHistoryById(id)
    }

    suspend fun clear() {
        historyDao.clearHistory()
    }
}
