package com.antoniopg.lupita.data

import com.antoniopg.lupita.core.model.CostLogEntry
import com.antoniopg.lupita.core.model.CostLogRepository
import com.antoniopg.lupita.data.db.CostLogDao
import com.antoniopg.lupita.data.db.toDomain
import com.antoniopg.lupita.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Implementacion Room de [CostLogRepository] (F4 paso 3). Traduce entidad<->dominio; ver CostLogEntity.kt. */
class RoomCostLogRepository(private val dao: CostLogDao) : CostLogRepository {
    override suspend fun record(entry: CostLogEntry) {
        dao.insert(entry.toEntity())
    }

    override suspend fun entriesSince(sinceMillis: Long): List<CostLogEntry> =
        dao.entriesSince(sinceMillis).mapNotNull { it.toDomain() }

    override fun observeRecent(limit: Int): Flow<List<CostLogEntry>> =
        dao.observeRecent(limit).map { list -> list.mapNotNull { it.toDomain() } }
}
