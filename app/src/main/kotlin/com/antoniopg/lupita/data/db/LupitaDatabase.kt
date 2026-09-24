package com.antoniopg.lupita.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Primera base de datos Room del proyecto (F4 paso 3). Version 2 (2026-09-25) anade
 * `analysis_history` (el texto real de un resultado, no solo su coste — ver AnalysisHistory.kt).
 * Sin `Migration`: `fallbackToDestructiveMigration` a proposito (app de uso personal, sin publicar,
 * un unico dispositivo de desarrollo — no hay datos de usuario real que perder todavia). Revisar
 * esta decision el dia que haya una version instalada fuera de este dispositivo.
 */
@Database(entities = [CostLogEntity::class, AnalysisHistoryEntity::class], version = 2, exportSchema = true)
abstract class LupitaDatabase : RoomDatabase() {
    abstract fun costLogDao(): CostLogDao
    abstract fun analysisHistoryDao(): AnalysisHistoryDao
}
