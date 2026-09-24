package com.antoniopg.lupita.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisHistoryDao {
    @Insert
    suspend fun insert(entity: AnalysisHistoryEntity): Long

    @Query("SELECT * FROM analysis_history ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AnalysisHistoryEntity>>
}
