package com.antoniopg.lupita.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CostLogDao {
    @Insert
    suspend fun insert(entity: CostLogEntity): Long

    /** Sin ordenar: la agregacion (sumar por herramienta/periodo) la hace el repositorio en Kotlin. */
    @Query("SELECT * FROM cost_log WHERE timestampMillis >= :sinceMillis")
    suspend fun entriesSince(sinceMillis: Long): List<CostLogEntity>

    @Query("SELECT * FROM cost_log ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CostLogEntity>>
}
