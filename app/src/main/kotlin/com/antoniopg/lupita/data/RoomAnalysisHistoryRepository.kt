package com.antoniopg.lupita.data

import com.antoniopg.lupita.core.model.AnalysisHistoryEntry
import com.antoniopg.lupita.core.model.AnalysisHistoryRepository
import com.antoniopg.lupita.data.db.AnalysisHistoryDao
import com.antoniopg.lupita.data.db.toDomain
import com.antoniopg.lupita.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Implementacion Room de [AnalysisHistoryRepository]. Mismo patron que [RoomCostLogRepository]. */
class RoomAnalysisHistoryRepository(private val dao: AnalysisHistoryDao) : AnalysisHistoryRepository {
    override suspend fun record(entry: AnalysisHistoryEntry) {
        dao.insert(entry.toEntity())
    }

    override fun observeRecent(limit: Int): Flow<List<AnalysisHistoryEntry>> =
        dao.observeRecent(limit).map { list -> list.mapNotNull { it.toDomain() } }
}
