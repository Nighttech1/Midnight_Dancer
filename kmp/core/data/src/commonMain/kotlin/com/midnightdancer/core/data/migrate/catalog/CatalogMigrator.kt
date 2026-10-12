package com.midnightdancer.core.data.migrate.catalog

import com.midnightdancer.core.data.db.CatalogDatabase
import com.midnightdancer.core.data.db.RoomCatalogRepository
import com.midnightdancer.core.data.db.entity.CatalogMetaEntity
import com.midnightdancer.core.data.db.entity.CatalogMetaKeys
import com.midnightdancer.core.data.migrate.CatalogMigrationHandOff
import com.midnightdancer.core.data.migrate.LegacyPrefsSource
import com.midnightdancer.core.domain.serialization.DomainJson

/**
 * Catalog half of Flutter → KMP migration: AppData JSON / [CatalogMigrationHandOff] → Room.
 *
 * Idempotent by entity id (Room `@Upsert`). Does **not** rewrite [com.midnightdancer.core.data.migrate.PrefsMigrator].
 */
class CatalogMigrator(
    private val repository: RoomCatalogRepository,
    private val db: CatalogDatabase,
) {
    data class Result(
        val applied: Boolean,
        val skippedAlreadyMigrated: Boolean,
        val source: LegacyPrefsSource,
        val entityCounts: EntityCounts = EntityCounts(),
    )

    data class EntityCounts(
        val styles: Int = 0,
        val moves: Int = 0,
        val songs: Int = 0,
        val choreographies: Int = 0,
    )

    suspend fun isCatalogMigrationDone(): Boolean =
        db.catalogMetaDao().get(CatalogMetaKeys.CATALOG_MIGRATION_DONE)?.value == "1"

    /**
     * Import from C-11 hand-off. Skips if marker already set (idempotent process flag).
     * Re-running upserts by id is still safe if you call [importAppDataJson] directly.
     */
    suspend fun migrateFromHandOff(handOff: CatalogMigrationHandOff?): Result {
        if (handOff == null || handOff.appDataJson.isBlank()) {
            return Result(
                applied = false,
                skippedAlreadyMigrated = false,
                source = LegacyPrefsSource.None,
            )
        }
        if (isCatalogMigrationDone()) {
            return Result(
                applied = false,
                skippedAlreadyMigrated = true,
                source = handOff.source,
            )
        }
        return importAppDataJson(handOff.appDataJson, handOff.source, markDone = true)
    }

    /**
     * Parse AppData JSON and upsert into Room. Stable ids → no duplicates.
     */
    suspend fun importAppDataJson(
        appDataJson: String,
        source: LegacyPrefsSource = LegacyPrefsSource.FilesystemMetadataJson,
        markDone: Boolean = true,
    ): Result {
        val appData =
            try {
                DomainJson.decodeAppData(appDataJson)
            } catch (_: Exception) {
                return Result(
                    applied = false,
                    skippedAlreadyMigrated = false,
                    source = source,
                )
            }

        repository.upsertAllIdempotent(appData)
        if (markDone) {
            db.catalogMetaDao().upsert(
                CatalogMetaEntity(
                    key = CatalogMetaKeys.CATALOG_MIGRATION_DONE,
                    value = "1",
                ),
            )
        }

        val moveCount = appData.danceStyles.sumOf { it.moves.size }
        return Result(
            applied = true,
            skippedAlreadyMigrated = false,
            source = source,
            entityCounts =
                EntityCounts(
                    styles = appData.danceStyles.size,
                    moves = moveCount,
                    songs = appData.songs.size,
                    choreographies = appData.choreographies.size,
                ),
        )
    }
}
