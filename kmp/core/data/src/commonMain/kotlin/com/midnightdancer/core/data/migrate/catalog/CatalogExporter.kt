package com.midnightdancer.core.data.migrate.catalog

import com.midnightdancer.core.data.db.RoomCatalogRepository
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.serialization.DomainJson

/**
 * Room → AppData JSON export for backup parity (consumed by C-15).
 */
class CatalogExporter(
    private val repository: RoomCatalogRepository,
) {
    suspend fun exportAppData(): AppData = repository.load()

    suspend fun exportAppDataJson(): String = DomainJson.encodeAppData(exportAppData())
}
