package com.midnightdancer.core.data.db

import androidx.room.withTransaction
import com.midnightdancer.core.data.db.entity.CatalogMetaEntity
import com.midnightdancer.core.data.db.entity.CatalogMetaKeys
import com.midnightdancer.core.data.db.mapper.CatalogMappers.toDomain
import com.midnightdancer.core.data.db.mapper.CatalogMappers.toEntity
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Choreography
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.core.domain.usecase.SaveAppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonObject

/**
 * Room-backed catalog repository implementing C-10 [LoadLibrary] / [SaveAppData].
 * Style 1—N Move; timeline persisted as JSON; settings blob in catalog_meta (export parity).
 */
class RoomCatalogRepository(
    private val db: CatalogDatabase,
) : LoadLibrary, SaveAppData {
    private val styles = db.danceStyleDao()
    private val moves = db.moveDao()
    private val songs = db.songDao()
    private val choreos = db.choreographyDao()
    private val meta = db.catalogMetaDao()

    override suspend fun load(): AppData = assembleAppData()

    override fun observe(): Flow<AppData> =
        combine(
            styles.observeAll(),
            moves.observeAll(),
            songs.observeAll(),
            choreos.observeAll(),
            meta.observeValue(CatalogMetaKeys.SETTINGS_JSON),
        ) { styleRows, moveRows, songRows, choreoRows, settingsRaw ->
            val movesByStyle = moveRows.groupBy { it.styleId }
            AppData(
                danceStyles =
                    styleRows.map { style ->
                        style.toDomain(
                            moves = (movesByStyle[style.id].orEmpty()).map { it.toDomain() },
                        )
                    },
                songs = songRows.map { it.toDomain() },
                choreographies = choreoRows.map { it.toDomain() },
                settings = decodeSettings(settingsRaw),
            )
        }

    override suspend fun save(data: AppData) {
        db.withTransaction {
            styles.deleteAll()
            moves.deleteAll()
            songs.deleteAll()
            choreos.deleteAll()
            writeCatalog(data)
        }
    }

    override suspend fun upsertSong(song: Song) {
        songs.upsert(song.toEntity())
    }

    override suspend fun upsertMove(styleId: String, move: Move) {
        val existing = moves.getByStyle(styleId)
        val order =
            existing.indexOfFirst { it.id == move.id }.takeIf { it >= 0 }
                ?: existing.size
        moves.upsert(move.toEntity(styleId = styleId, sortOrder = order))
    }

    override suspend fun upsertStyle(style: DanceStyle) {
        db.withTransaction {
            styles.upsert(style.toEntity())
            moves.deleteByStyle(style.id)
            moves.upsertAll(
                style.moves.mapIndexed { index, move ->
                    move.toEntity(styleId = style.id, sortOrder = index)
                },
            )
        }
    }

    override suspend fun upsertChoreography(choreography: Choreography) {
        choreos.upsert(choreography.toEntity())
    }

    /** Idempotent upsert of full AppData (used by catalog migrator). */
    suspend fun upsertAllIdempotent(data: AppData) {
        db.withTransaction {
            writeCatalog(data)
        }
    }

    private suspend fun writeCatalog(data: AppData) {
        styles.upsertAll(data.danceStyles.map { it.toEntity() })
        val moveEntities =
            data.danceStyles.flatMap { style ->
                style.moves.mapIndexed { index, move ->
                    move.toEntity(styleId = style.id, sortOrder = index)
                }
            }
        moves.upsertAll(moveEntities)
        songs.upsertAll(data.songs.map { it.toEntity() })
        choreos.upsertAll(data.choreographies.map { it.toEntity() })
        meta.upsert(
            CatalogMetaEntity(
                key = CatalogMetaKeys.SETTINGS_JSON,
                value = DomainJson.format.encodeToString(JsonObject.serializer(), data.settings),
            ),
        )
    }

    private suspend fun assembleAppData(): AppData {
        val styleRows = styles.getAll()
        val moveRows = moves.getAll().groupBy { it.styleId }
        return AppData(
            danceStyles =
                styleRows.map { style ->
                    style.toDomain(
                        moves = (moveRows[style.id].orEmpty()).map { it.toDomain() },
                    )
                },
            songs = songs.getAll().map { it.toDomain() },
            choreographies = choreos.getAll().map { it.toDomain() },
            settings = decodeSettings(meta.get(CatalogMetaKeys.SETTINGS_JSON)?.value),
        )
    }

    private fun decodeSettings(raw: String?): JsonObject {
        if (raw.isNullOrBlank()) return JsonObject(emptyMap())
        return runCatching {
            DomainJson.format.decodeFromString(JsonObject.serializer(), raw)
        }.getOrElse { JsonObject(emptyMap()) }
    }
}
