package com.antoniopg.lupita.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Primera base de datos Room del proyecto (F4 paso 3). Solo el log de coste por ahora — un futuro
 * Artifact/cache persistente (hoy [com.antoniopg.lupita.orchestrator.InMemoryArtifactCache], en
 * memoria a proposito) anadiria su propia entidad aqui, no una BD aparte.
 */
@Database(entities = [CostLogEntity::class], version = 1, exportSchema = true)
abstract class LupitaDatabase : RoomDatabase() {
    abstract fun costLogDao(): CostLogDao
}
